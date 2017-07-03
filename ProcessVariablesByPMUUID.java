
package com.pshealth.smartservices.dynamicpv;

/**
 *
 * @author CAZ
 */


import com.appiancorp.plugins.typetransformer.AppianElement;
import com.appiancorp.plugins.typetransformer.AppianList;
import com.appiancorp.plugins.typetransformer.AppianObject;
import com.appiancorp.plugins.typetransformer.AppianTypeFactory;
import org.apache.commons.lang.StringUtils;

import com.appiancorp.services.ServiceContext;
import com.appiancorp.suiteapi.common.ServiceLocator;
import com.appiancorp.suiteapi.common.exceptions.InvalidProcessModelException;
import com.appiancorp.suiteapi.common.exceptions.PrivilegeException;
import com.appiancorp.suiteapi.content.ContentService;
import com.appiancorp.suiteapi.expression.annotations.Function;
import com.appiancorp.suiteapi.expression.annotations.Parameter;
import com.appiancorp.suiteapi.process.ProcessDesignService;
import com.appiancorp.suiteapi.process.ProcessModel;
import com.appiancorp.suiteapi.process.ProcessVariable;
import com.appiancorp.suiteapi.type.AppianType;
import com.appiancorp.suiteapi.type.Datatype;
import com.appiancorp.suiteapi.type.TypeService;
import com.appiancorp.suiteapi.type.TypedValue;
import com.appiancorp.suiteapi.type.exceptions.InvalidTypeException;
import com.appiancorp.type.DataType;

@com.appiancorp.suiteapi.expression.annotations.AppianScriptingFunctionsCategory
public class ProcessVariablesByPMUUID{
 

  @Function
  public TypedValue getPVDetailsByProcessModelUUIDWithDefaultValue(ServiceContext sc, ProcessDesignService pd,
    ContentService cs, @Parameter String processModelUUIDOrId) throws InvalidTypeException {
    
    ProcessModel processModel;
 
    
    try {

      try {     
        processModelUUIDOrId = StringUtils.trimToEmpty(processModelUUIDOrId);
        processModel = pd.getProcessModel(Long.parseLong(processModelUUIDOrId));        

      } catch (NumberFormatException e) {
      
        Long pmId = pd.getProcessModelIdByUuid(processModelUUIDOrId);
        processModel = pd.getProcessModel(pmId);                
      }     
      
      TypeService ts = ServiceLocator.getTypeService(sc);
      AppianTypeFactory tf =  AppianTypeFactory.newInstance(ts);
      AppianList list = tf.createList(AppianType.DICTIONARY);
      
      ProcessVariable Variables[] = processModel.getVariables(); 



      for(int i = 0; i<Variables.length;i++)
      {       
        AppianObject dictionary = (AppianObject) tf.createElement(AppianType.DICTIONARY);
        dictionary.put("name", tf.createString(Variables[i].getFriendlyName()));
        Datatype dt = ts.getType(Variables[i].getInstanceType());
        dictionary.put("type", tf.createString(dt.getName()));
        TypedValue tv = new TypedValue(dt.getId());
        AppianElement ae = tf.toAppianElement(tv);
        dictionary.put("defaultValue",  tf.createString(ae.toString()));
        dictionary.put("typeID",  tf.createLong(dt.getId()));
        list.add(dictionary);
      }

      return tf.toTypedValue(list);

    } catch (InvalidProcessModelException | PrivilegeException e) {     
      return null;
    } 
  }

  
  
  
  
}
