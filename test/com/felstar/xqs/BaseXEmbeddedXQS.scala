package com.felstar.xqs

import com.felstar.xqs.XQS._
import com.felstar.xqs.XQS.AllImplicits._
import scala.xml.PrettyPrinter
import com.xqj2.XQConnection2

object BaseXEmbeddedXQS extends App {

  val conn =  new net.xqj.basex.local.BaseXXQDataSource().getConnection.asInstanceOf[XQConnection2]
 
  val strings: Seq[String] = conn("1 to 4")
   
  strings.foreach(println)
  
  val xqe = conn.createExpression()
   
  val db="xmlstore"
  
  xqe.executeCommand(s"CHECK $db")
  
  xqe.executeCommand("SET DEFAULTDB true")

  {
   val sampleXML= <root>
		  <child>John</child>
		  <child>Mary</child>
		  </root>
  
   val item=conn.createItemFromNode(sampleXML, null)
 
   conn.insertItem("sampleXML.xml",item,null)
  } 
  
  {
   val item=conn.createItemFromNode( <root2>Another one</root2>, null)
 
   conn.insertItem("sampleXML2.xml",item,null)
  }
  val elems:Seq[scala.xml.Elem] = conn("doc('sampleXML.xml'),doc('sampleXML2.xml')")
  
  val pp=new PrettyPrinter(80,2)

  elems foreach(x=>println(pp.format(x)))    
     
  conn.close()
}