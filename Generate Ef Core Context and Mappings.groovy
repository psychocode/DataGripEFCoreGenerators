import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil

typeMapping = [
        (~/(?i)tinyint/)                                : "byte",
        (~/(?i)bit/)                                    : "bool",
        (~/(?i)uniqueidentifier|uuid/)                  : "Guid",
        (~/(?i)int|integer/)                            : "int",
        (~/(?i)bigint/)                                  : "long",
        (~/(?i)varbinary|image/)                        : "byte[]",
        (~/(?i)double|float|real/)                      : "double",
        (~/(?i)decimal|money|numeric|smallmoney|number/): "decimal",
        (~/(?i)datetimeoffset/)                         : "DateTimeOffset",
        (~/(?i)datetime|datetime2|timestamp|date|time/) : "DateTime",
        (~/(?i)^char/)                                   : "char",
]

notNullableTypes = [ "string", "byte[]" ]

FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    def tables = SELECTION.filter { it instanceof DasTable && it.getKind() == ObjectKind.TABLE || it.getKind() == ObjectKind.VIEW }

    generateContextFile(tables, dir)
    tables.each { generateEntities(it, dir) }

}

def generateContextFile(tables, dir)
{
    new File(dir, "ahhContext.cs").withPrintWriter { out -> generateContext(out, tables) }
}

def generateEntities(table, dir) {
    def className = csharpName(table.getName())
    def fields = calcFields(table)
    def tableName = table.getName()
    new File(dir, className + ".cs").withPrintWriter { out -> generateClass(out, className, fields) }

    if (table.getKind() == ObjectKind.TABLE)
        new File(dir, className + "Mapping" + ".cs").withPrintWriter { out -> generateTableClassMapping(out, tableName, className, fields) }
    if (table.getKind() == ObjectKind.VIEW)
        new File(dir, className + "Mapping" + ".cs").withPrintWriter { out -> generateViewClassMapping(out, tableName, className, fields) }

}


def generateContext(out, tables)
{
    out.println "using System;"
    out.println "using Microsoft.EntityFrameworkCore;"
    out.println ""
    out.println "public class Context: DbContext"
    out.println "{"

    tables.each() {
        def contextPropertyType = dbContextPropertyType(it)
        def className = csharpName(it.getName())
        out.println "   public ${contextPropertyType}<${className}> ${className}s { get; set; }"
    }

    out.println ""
    out.println "   public Context(DbContextOptions<Context> options) : base(options) {}"

    out.println ""
    out.println "   protected override void OnModelCreating(ModelBuilder modelBuilder)"
    out.println "   {"

    tables.each() {
        def className = csharpName(it.getName())
        out.println("       modelBuilder.ApplyConfiguration(new ${className}Map());")
    }

    out.println "   }"
    out.println "}"
}

def generateClass(out, className, fields) {
    out.println "using System;"
    out.println ""
    out.println "public class $className"
    out.println "{"

    fields.each() {
        out.println "    public ${it.type} ${it.name} { get; set; }"
    }
    out.println "}"
}

def generateViewClassMapping(out, viewName, className, fields) {
    out.println "using System;"
    out.println "using Microsoft.EntityFrameworkCore;"
    out.println "using Microsoft.EntityFrameworkCore.Metadata.Builders;"
    out.println ""
    out.println "public class ${className}Map : IQueryTypeConfiguration<${className}> "
    out.println "{"
    out.println ""
    out.println "   public void Configure(QueryTypeBuilder<${className}> builder)"
    out.println "   {"
    out.println "       builder.ToView(\"$viewName\");"

    fields.each() {
        out.println "       builder.Property(p => p.${it.name}).HasColumnName(\"${it.colName}\").HasColumnType(\"${it.colType}\");"
    }

    out.println "   }"
    out.println "}"
}

def generateTableClassMapping(out, tableName, className, fields) {
    out.println "using System;"
    out.println "using Microsoft.EntityFrameworkCore;"
    out.println "using Microsoft.EntityFrameworkCore.Metadata.Builders;"
    out.println ""
    out.println "public class ${className}Map : IEntityTypeConfiguration<${className}> "
    out.println "{"
    out.println ""
    out.println "   public void Configure(EntityTypeBuilder<${className}> builder)"
    out.println "   {"
    out.println "       builder.ToTable(\"$tableName\");"

    fields.each() {
        out.println "       builder.Property(p => p.${it.name}).HasColumnName(\"${it.colName}\").HasColumnType(\"${it.colType}\");"
    }

    out.println "   }"
    out.println "}"
}

def calcFields(table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->
        def spec = Case.LOWER.apply(col.getDataType().getSpecification())
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }?.value ?: "string"
        def nullable = col.isNotNull() || typeStr in notNullableTypes ? "" : "?"
        fields += [[
                           name : csharpName(col.getName()),
                           colName: col.getName(),
                           type : typeStr + nullable,
                           colType: col.getDataType().getSpecification()
                           ]]
    }
}

def csharpName(str) {
    com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
}

def dbContextPropertyType(table) {
 if (table.getKind() == ObjectKind.TABLE)
    return "DbSet"
 if (table.getKind() == ObjectKind.VIEW)
    return "DbQuery"
}