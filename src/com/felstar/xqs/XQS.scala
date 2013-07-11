package com.felstar.xqs

import java.io.StringReader

import xml.Attribute
import xml.Node
import xml.Null
import xml.Text
import xml.XML
import xml.parsing.NoBindingFactoryAdapter

import org.xml.sax.InputSource

import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.sax.SAXResult
import javax.xml.xquery.XQConnection
import javax.xml.xquery.XQConstants
import javax.xml.xquery.XQDynamicContext
import javax.xml.xquery.XQItem
import javax.xml.xquery.XQItemType
import javax.xml.xquery.XQPreparedExpression
import javax.xml.xquery.XQSequence
import javax.xml.xquery.XQResultSequence

/**
 * XQS is a Scala API that sits atop XQJ and provides Scala interfaces and metaphors
 * Comments and suggestions are welcome. Use this file as you will.
 * Would be nice if I got attribution. Thanks. 
 * @author Dino Fancellu (Felstar Ltd)
 * @version 0.8
 * 
 */
object XQS {

  import AllImplicits._
  
  val tFactory = javax.xml.transform.TransformerFactory.newInstance
  val builderFactory= DocumentBuilderFactory.newInstance();

  private var seq2expression=Map[XQSequence,XQPreparedExpression]()
	
  def execute(expr:XQPreparedExpression)={     
    val seq=expr.executeQuery()
    synchronized {
	 seq2expression+=(seq->expr)
    }
	seq
  }
	// closes sequence and its expression
  def closeResultSequence(seq:XQSequence){ 
	//strictly not needed, closing expression should do it
	seq.close
	synchronized {
	 seq2expression.get(seq).foreach(_.close)
	 seq2expression-=seq
    }
  }
   // Useful if you simply want to close all expressions
  def closeAllResultSequences
  {
    synchronized {
     seq2expression.keys.foreach(closeResultSequence(_)) 
    }   
  }
  
	// useful for debug, should be empty at end of run
  def getExpressionMap=seq2expression
  
  implicit def toQName(name: String): QName = new QName(name)
    
  implicit def toScala(dom: _root_.org.w3c.dom.Node): Node = {
    val adapter = new NoBindingFactoryAdapter
    tFactory.newTransformer().transform(new DOMSource(dom), new SAXResult(adapter))
    adapter.rootElem
  }
  
   implicit def toDom(node: scala.xml.Node): org.w3c.dom.Node = {
    val str=node.buildString(false)
    builderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(str)))
  }

  implicit def toDomSource(node: scala.xml.Node): DOMSource =new DOMSource(toDom(node))
   
  implicit def toScala(attr: org.w3c.dom.Attr): Attribute 
  			= Attribute(None, attr.getName, Text(attr.getValue), Null)  

  implicit def toIterator[A](s: Seq[A]):java.util.Iterator[A] 
		  =scala.collection.JavaConverters.seqAsJavaListConverter(s).asJava.iterator()
  
  def toSeqAnyRef(s: XQSequence):Seq[AnyRef] = {
      var seq = Seq[AnyRef]()
      try {
       while (s.next()) {
        seq +:= (s.getObject match {
          case x: org.w3c.dom.Element => toScala(x)
          case x: org.w3c.dom.Attr => toScala(x)
          case x: org.w3c.dom.Node => toScala(x)          
          case x =>  x
        })
       }
      } finally { closeResultSequence(s) }
      seq reverse
    }

   implicit def toSeqString(s: XQSequence):Seq[String] = {
      var seq = Seq[String]()
      try { while (s.next()) seq +:= s.getItemAsString(null)} 
       finally {closeResultSequence(s)}
      seq reverse
    }
   
    implicit def toSeqInt(s: XQSequence):Seq[Int] = {
      var seq = Seq[Int]()
      try { while (s.next()) seq +:= s.getInt} finally {closeResultSequence(s)}
      seq reverse
    }
    
    implicit def toSeqDecimal(s: XQSequence):Seq[scala.math.BigDecimal] = {
      var seq = Seq[scala.math.BigDecimal]()
	  try {
	      while (s.next()) {
	        seq +:= (s.getObject match {
	          case x: java.math.BigDecimal => scala.math.BigDecimal(x)
	          case x: java.math.BigInteger => scala.math.BigDecimal(x)
	          case x: java.lang.Double => scala.math.BigDecimal(x)
	          case x: java.lang.Long => scala.math.BigDecimal(x)
	          case x: java.lang.Integer => scala.math.BigDecimal(x)
	          case x: java.lang.Short => scala.math.BigDecimal(x.shortValue)
	          case x: java.lang.Byte => scala.math.BigDecimal(x.intValue)
	          case x: java.lang.String => scala.math.BigDecimal(x)
	        })
	      }
	  } finally { closeResultSequence(s) }
      seq reverse
    }
  
  implicit def toSeqXML(seq: Seq[String]):Seq[scala.xml.Elem] = seq.map(XML.loadString)
  implicit def toSeqXML(s: XQSequence):Seq[scala.xml.Elem] = toSeqXML(toSeqString(s))  
  
 trait ImplicitXQConnection{
  implicit class MyXQConnection(val conn:XQConnection) {   
    def executeQuery(query:String)=execute(conn.prepareExpression(query))
    def executeQuery(query:java.io.InputStream)=execute(conn.prepareExpression(query))
    def executeQuery(query:java.io.Reader )=execute(conn.prepareExpression(query))    
    def executeQuery(query:String,xml:scala.xml.Elem)={
      execute(conn.prepareExpression(query).document(XQConstants.CONTEXT_ITEM,xml))
    }   
    def executeQuery(query:java.io.InputStream,xml:scala.xml.Elem)={
       execute(conn.prepareExpression(query).document(XQConstants.CONTEXT_ITEM,xml))
    }   
    def executeQuery(query:java.io.Reader,xml:scala.xml.Elem)={
       execute(conn.prepareExpression(query).document(XQConstants.CONTEXT_ITEM,xml))
    }
    
    def apply(query:String,xml:scala.xml.Elem)=executeQuery(query,xml)
    def apply(query:java.io.InputStream,xml:scala.xml.Elem)=executeQuery(query,xml)
    def apply(query:java.io.Reader,xml:scala.xml.Elem)=executeQuery(query,xml)    
    def apply(query:String)=executeQuery(query)
    def apply(query:java.io.InputStream)=executeQuery(query)
    def apply(query:java.io.Reader)=executeQuery(query)
  }
 }
  
  trait ImplicitXQExpression  {    
    
   implicit class MyXQExpression[A <:XQPreparedExpression](val expr:A) {
     def execute(): XQResultSequence=XQS.execute(expr)
   }
    
   implicit class  MyXQDynamicContext[A <:XQDynamicContext](val context:A) {    
     
    def document(varName:javax.xml.namespace.QName, value:String , baseURI:String)= {
      context.bindDocument(varName, value, baseURI, null);context
    }
    def document(varName:javax.xml.namespace.QName, 
        value:javax.xml.transform.Source )= {
      context.bindDocument(varName, value, null);context
    }
    def document(varName:javax.xml.namespace.QName, 
        value:java.io.Reader,  baseURI:String)= {
      context.bindDocument(varName, value, baseURI, null);context
    }    
    def document(varName:javax.xml.namespace.QName, 
        value:java.io.InputStream ,baseURI:String)= {
      context.bindDocument(varName, value, baseURI,null);context
    }       
     def boolean(varName:javax.xml.namespace.QName,value:Boolean)= {
        context.bindBoolean(varName,value,null);context
     }
     def byte(varName:javax.xml.namespace.QName,value:Byte)= {
        context.bindByte(varName,value,null);context
     }
     def double(varName:javax.xml.namespace.QName,value:Double)= {
        context.bindDouble(varName,value,null);context
     }
     def float(varName:javax.xml.namespace.QName,value:Float)= {
        context.bindFloat(varName,value,null);context
     }
     def int(varName:javax.xml.namespace.QName,value:Int)= {
        context.bindInt(varName,value,null);context
     }
     def item(varName:javax.xml.namespace.QName,value:XQItem)= {
        context.bindItem(varName,value);context
     }
     def long(varName:javax.xml.namespace.QName,value:Long)= {
        context.bindLong(varName,value,null);context
     }
     def node(varName:javax.xml.namespace.QName,
         value:org.w3c.dom.Node, typ: XQItemType=null)= {
        context.bindNode(varName,value,typ);context
     }
     // Sorry object is reserved
     def obj(varName:javax.xml.namespace.QName,value:AnyRef, typ: XQItemType=null)= {
        context.bindObject(varName,value,typ);context
     }
     def sequence(varName:javax.xml.namespace.QName,value:XQSequence)= {
        context.bindSequence(varName,value);context        
     }
     def short(varName:javax.xml.namespace.QName,value:Short)= {
        context.bindShort(varName,value,null);context
     }
     def string(varName:javax.xml.namespace.QName,value:String)= {
        context.bindString(varName,value,null);context
     }
   }
  }
  
  object AllImplicits extends ImplicitXQConnection with ImplicitXQExpression  
}