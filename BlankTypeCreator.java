
package com.pshealth.smartservices.dynamicpv;

/**
 *
 * @author CAZ
 */



import com.appiancorp.plugins.typetransformer.AppianObject;
import com.appiancorp.plugins.typetransformer.AppianTypeFactory;
import com.appiancorp.services.ServiceContext;
import com.appiancorp.suiteapi.common.ServiceLocator;
import com.appiancorp.suiteapi.content.ContentService;
import com.appiancorp.suiteapi.expression.annotations.Function;
import com.appiancorp.suiteapi.expression.annotations.Parameter;
import com.appiancorp.suiteapi.process.ProcessDesignService;
import com.appiancorp.suiteapi.type.TypeService;
import com.appiancorp.suiteapi.type.TypedValue;
import javax.xml.namespace.QName;

@com.appiancorp.suiteapi.expression.annotations.AppianScriptingFunctionsCategory
public class BlankTypeCreator{
 
  
  
    @Function
  public static String createBlankTypeByQualifiedName(ServiceContext sc, ProcessDesignService pd,
    ContentService cs, @Parameter String namespace, @Parameter String localName)  {
    
    TypeService ts = ServiceLocator.getTypeService(sc);
    Long TypeID = ts.getTypeByQualifiedName(new QName(namespace,localName)).getId();
    TypedValue tv = new TypedValue(TypeID);
    AppianTypeFactory tf =  AppianTypeFactory.newInstance(ts);
    return ((AppianObject) tf.toAppianElement(tv)).toString();
    
  }

  
  
  
  
}
