package org.squeryl.dsl.ast


import collection.mutable.ArrayBuffer
import org.squeryl.internals._
import org.squeryl.Session
import org.squeryl.dsl.ExpressionKind


trait ExpressionNode {

  var parent: Option[ExpressionNode] = None

  def id = Integer.toHexString(System.identityHashCode(this))

  def inhibited = false
  
  def inhibitedFlagForAstDump =
    if(inhibited) "!" else ""

  def write(sw: StatementWriter) =
    if(!inhibited)
      doWrite(sw)

  def doWrite(sw: StatementWriter): Unit

  def writeToString: String = {
    val sw = new StatementWriter(Session.currentSession.databaseAdapter)
    write(sw)
    sw.statement
  }

  def children: List[ExpressionNode] = List.empty
  
  override def toString = this.getClass.getName

  private def _visitDescendants(
          n: ExpressionNode, parent: Option[ExpressionNode], depth: Int,
          visitor: (ExpressionNode,Option[ExpressionNode],Int) => Unit): Unit = {
    visitor(n, parent, depth)
    n.children.foreach(child => _visitDescendants(child, Some(n), depth + 1, visitor))
  }


  private def _filterDescendants(n: ExpressionNode, ab: ArrayBuffer[ExpressionNode], predicate: (ExpressionNode) => Boolean): Iterable[ExpressionNode] = {
    if(predicate(n))
      ab.append(n)
    n.children.foreach(child => _filterDescendants(child, ab, predicate))
    ab
  }

  def filterDescendants(predicate: (ExpressionNode) => Boolean) =
    _filterDescendants(this, new ArrayBuffer[ExpressionNode], predicate)


  def filterDescendantsOfType[T](implicit manifest: Manifest[T]) =
    _filterDescendants(
      this,
      new ArrayBuffer[ExpressionNode],
      (n:ExpressionNode)=> manifest.erasure.isAssignableFrom(n.getClass)
    ).asInstanceOf[Iterable[T]]

  /**
   * visitor's args are :
   *  -the visited node,
   *  -it's parent
   *  -it's depth
   */
  def visitDescendants(visitor: (ExpressionNode,Option[ExpressionNode],Int) => Unit) =
    _visitDescendants(this, None, 0, visitor)
}


trait ListExpressionNode extends ExpressionNode {
  def quotesElement = false
}

trait ListNumerical extends ListExpressionNode


trait ListDouble extends ListNumerical
trait ListFloat  extends ListNumerical
trait ListInt extends ListNumerical
trait ListLong extends ListNumerical
trait ListDate extends ListExpressionNode

trait ListString extends ListExpressionNode {
  override def quotesElement = true
}

trait LogicalBoolean

//TODO: erase type A, it is unneeded, and use ExpressionNode instead of TypedExp...
class UpdateAssignment(val left: TypedExpressionNode[_,_], val right: TypedExpressionNode[_,_])

trait TypedExpressionNode[K <: ExpressionKind,T] extends ExpressionNode {
  def :=[B <% TypedExpressionNode[K,T]] (b: B) = new UpdateAssignment(this, b : TypedExpressionNode[K,T])

  //def <[B <% TypedExpressionNode[Scalar,T]] (b: B) = new ScalarBoolean...
}

class TokenExpressionNode(val token: String) extends ExpressionNode {
  def doWrite(sw: StatementWriter) = sw.write(token)
}

class ConstantExpressionNode[T](val value: T, needsQuote: Boolean) extends ExpressionNode {

  def this(v:T) = this(v, false)

  def doWrite(sw: StatementWriter) = {
    if(sw.isForDisplay) {
      if(value == null)
        sw.write("null")
      else if(needsQuote) {
        sw.write("'")
        sw.write(value.toString)
        sw.write("'")
      }
      else
        sw.write(value.toString)
    }
    else {
      sw.write("?")
      sw.addParam(value.asInstanceOf[AnyRef])
    }
  }
  override def toString = 'ConstantExpressionNode + ":" + value
}

class ConstantExpressionNodeList[T](val value: List[T]) extends ExpressionNode with ListExpressionNode {
  
  def doWrite(sw: StatementWriter) =
    if(quotesElement)
      sw.write(this.value.map(e=>"'" +e+"'").mkString("(",",",")"))
    else
      sw.write(this.value.mkString("(",",",")"))
}

class FunctionNode(val name: String, val args: Iterable[ExpressionNode]) extends ExpressionNode {

  def this(name: String, args: ExpressionNode*) = this(name, args)
  
  def doWrite(sw: StatementWriter) = {

    sw.write(name)
    sw.write("(")
    sw.writeNodesWithSeparator(args, ",", false)
    sw.write(")")
  }
  
  override def children = args.toList
}

class BinaryOperatorNode
 (val left: ExpressionNode, val right: ExpressionNode, val operatorToken: String, val newLineAfterOperator: Boolean = false)
  extends ExpressionNode {

  override def children = List(left, right)

  override def inhibited =
    left.inhibited || right.inhibited 

  override def toString =
    'BinaryOperatorNode + ":" + operatorToken + inhibitedFlagForAstDump
  
  def doWrite(sw: StatementWriter) = {
    sw.write("(")
    left.write(sw)
    sw.write(" ")
    sw.write(operatorToken)
    if(newLineAfterOperator)
      sw.nextLine
    sw.write(" ")
    right.write(sw)
    sw.write(")")
  }
}

class LeftOuterJoinNode
 (left: ExpressionNode, right: ExpressionNode)
  extends BinaryOperatorNode(left,right, "left", false) {

  override def doWrite(sw: StatementWriter) = {}
  
  override def toString = 'LeftOuterJoin + ""  
}

class FullOuterJoinNode(left: ExpressionNode, right: ExpressionNode) extends BinaryOperatorNode(left, right, "full", false) {
  override def toString = 'FullOuterJoin + ""
}

trait UniqueIdInAliaseRequired  {
  var uniqueId: Option[Int] = None 
}

trait QueryableExpressionNode extends ExpressionNode with UniqueIdInAliaseRequired {

  private var _inhibited = false

  override def inhibited = _inhibited

  def inhibited_=(b: Boolean) = _inhibited = b

  /**
   * outerJoinColumns is None if not an outer join, args are (left col : SelectElementReference, right col : SelectElementReference, outer Join kind: String ("left" or "full")) 
   */
  var outerJoinColumns: Option[(SelectElementReference,SelectElementReference, String)] = None

  var fullOuterJoinMatchColumn: Option[SelectElementReference] = None

  def isOptionalInOuterJoin =
    outerJoinColumns != None || fullOuterJoinMatchColumn != None
  
  def dumpOuterJoinInfoForAst(sb: StringBuffer) = {
    if(outerJoinColumns != None) {
      sb.append("OuterJoin(")
      sb.append(outerJoinColumns.get._1)
      sb.append(" ~> ")
      sb.append(outerJoinColumns.get._2)
      sb.append(")")
    }
    if(fullOuterJoinMatchColumn != None) {
      sb.append("FullOuterJoin(")
      sb.append(fullOuterJoinMatchColumn.get)
      sb.append(")")
    }
  }
  
  def isChild(q: QueryableExpressionNode): Boolean  

  def owns(aSample: AnyRef): Boolean
  
  def alias: String

  def getOrCreateSelectElement(fmd: FieldMetaData, forScope: QueryExpressionElements): SelectElement

  def getOrCreateAllSelectElements(forScope: QueryExpressionElements): Iterable[SelectElement]

  def dumpAst = {
    val sb = new StringBuffer
    visitDescendants {(n,parent,d:Int) =>
      val c = 4 * d
      for(i <- 1 to c) sb.append(' ')
      sb.append(n)
      sb.append("\n")
    }
    sb.toString
  }  
}
