package org.squeryl.adapters

import org.squeryl.internals.{ConstantStatementParam, StatementWriter, FieldMetaData, DatabaseAdapter}
import java.sql.SQLException
import org.squeryl.{InternalFieldMapper, Schema}
import org.squeryl.dsl.ast.ExpressionNode

class HsqldbAdapter extends DatabaseAdapter {
  override def isTableDoesNotExistException(e: SQLException): Boolean = {
    e.getErrorCode == -5501 && e.getSQLState == "42501"
  }

  val ARRAY_DECL = " ARRAY"

  // For string arrays, hsqld requires an explicit max size.
  override def stringArrayTypeDeclaration: String = stringTypeDeclaration + "(16M)" + ARRAY_DECL
  override def doubleArrayTypeDeclaration: String = doubleTypeDeclaration + ARRAY_DECL
  override def longArrayTypeDeclaration: String = longTypeDeclaration + ARRAY_DECL
  override def intArrayTypeDeclaration: String = intTypeDeclaration + ARRAY_DECL

  override def binaryTypeDeclaration: String = "LONGVARBINARY"

  override def supportsAutoIncrementInColumnDeclaration: Boolean = false

  override def writeColumnDeclaration(fmd: FieldMetaData, isPrimaryKey: Boolean, schema: Schema): String = {
    var res = super.writeColumnDeclaration(fmd, isPrimaryKey, schema)

    if (fmd.isAutoIncremented)
      res += " GENERATED BY DEFAULT AS IDENTITY(START WITH 1)"

    res
  }

  override def writeRegexExpression(left: ExpressionNode, pattern: String, sw: StatementWriter): Unit = {
    sw.write("REGEXP_MATCHES(")
    left.write(sw)
    sw.write(", ?)")
    sw.addParam(ConstantStatementParam(InternalFieldMapper.stringTEF.createConstant(pattern)))
  }

  override def quoteIdentifier(s: String): String = "\"%s\"".format(s)

  override def writeCreateSchema(name: String): Option[String] = Some("CREATE SCHEMA %s".format(quoteIdentifier(name)))

  override def writeDropSchema(name: String): Option[String] = Some("DROP SCHEMA %s".format(quoteIdentifier(name)))
}