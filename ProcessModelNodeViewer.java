
package com.pshealth.smartservices.dynamicpv;

/**
 *
 * @author CAZ
 */


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
import com.appiancorp.suiteapi.process.ProcessNode;
import com.appiancorp.suiteapi.type.AppianType;
import com.appiancorp.suiteapi.type.TypeService;
import com.appiancorp.suiteapi.type.TypedValue;
import com.appiancorp.suiteapi.type.exceptions.InvalidTypeException;

@com.appiancorp.suiteapi.expression.annotations.AppianScriptingFunctionsCategory
public class ProcessModelNodeViewer{
 

  @Function
  public TypedValue getNodeDetailsForPMUUID(ServiceContext sc, ProcessDesignService pd,
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
      
      ProcessNode[] pnArray = processModel.getProcessNodes();
      
      TypeService ts = ServiceLocator.getTypeService(sc);
      AppianTypeFactory tf =  AppianTypeFactory.newInstance(ts);
      AppianList list = tf.createList(AppianType.DICTIONARY);   
      
      for(int i = 0; i<pnArray.length;i++)
      {
          AppianObject dictionary = (AppianObject) tf.createElement(AppianType.DICTIONARY);
          dictionary.put("name", tf.createString(pnArray[i].getFriendlyName().toString()));
          dictionary.put("uuid", tf.createString(pnArray[i].getUuid()));
          list.add(dictionary);
      }

      return tf.toTypedValue(list);

    } catch (InvalidProcessModelException | PrivilegeException e) {     
      return null;
    } 
  }

  
  
  
  
}
