/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pshealth.smartservices.dynamicpv;

/**
 *
 * @author CAZ
 */


import com.appiancorp.plugins.typetransformer.AppianElement;
import com.appiancorp.plugins.typetransformer.AppianList;
import com.appiancorp.plugins.typetransformer.AppianObject;
import com.appiancorp.plugins.typetransformer.AppianPrimitive;
import com.appiancorp.plugins.typetransformer.AppianTypeFactory;
import java.util.List;

import com.appiancorp.services.ServiceContext;
import com.appiancorp.services.ServiceContextFactory;
import com.appiancorp.suiteapi.common.Constants;
import com.appiancorp.suiteapi.common.Name;
import com.appiancorp.suiteapi.common.ResultPage;
import com.appiancorp.suiteapi.common.ServiceLocator;
import com.appiancorp.suiteapi.common.exceptions.AppianException;
import com.appiancorp.suiteapi.common.exceptions.ErrorCode;
import com.appiancorp.suiteapi.process.ActivityClassParameter;
import com.appiancorp.suiteapi.process.ProcessExecutionService;
import com.appiancorp.suiteapi.process.ProcessVariableInstance;
import com.appiancorp.suiteapi.process.exceptions.SmartServiceException;
import com.appiancorp.suiteapi.process.framework.AppianSmartService;
import com.appiancorp.suiteapi.process.framework.Input;
import com.appiancorp.suiteapi.process.framework.MessageContainer;
import com.appiancorp.suiteapi.process.framework.Required;
import com.appiancorp.suiteapi.process.framework.SmartServiceContext;
import com.appiancorp.suiteapi.process.palette.PaletteInfo;
import com.appiancorp.suiteapi.type.AppianType;
import com.appiancorp.suiteapi.type.NamedTypedValue;
import com.appiancorp.suiteapi.type.TypeService;
import com.appiancorp.suiteapi.type.TypedValue;
import com.appiancorp.suiteapi.type.exceptions.InvalidTypeException;
import java.io.StringReader;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;

@PaletteInfo(paletteCategory = "Integration Services", palette = "Connectivity Services")
public class SetExternalPVDynamic extends AppianSmartService {

	private final SmartServiceContext smartServiceCtx;
	private Long processId;
	private String[] targetPvNames;
	private String operation;
	private String type;
	private String xml;
	private static Object NODE_INSTANCE_IN_PROGRESS_LOCK = new Object();
	private static final String OPERATION_SET = "set";
	private static final String OPERATION_APPEND = "append";
	private List<? extends NamedTypedValue> customInputs;

	@Override
	public void run() throws SmartServiceException {
		ServiceContext sc = null;
		ProcessExecutionService pes = null;
		TypeService ts = null;
		try {
			synchronized (NODE_INSTANCE_IN_PROGRESS_LOCK) {
				sc = ServiceContextFactory.getServiceContext(smartServiceCtx.getUsername());
				pes = ServiceLocator.getProcessExecutionService(sc);
				ts = ServiceLocator.getTypeService(sc);
				ResultPage rp = pes.getProcessVariablesPaging(processId, 0, Constants.COUNT_ALL,
						Constants.SORT_ORDER_ASCENDING, ProcessVariableInstance.SORT_BY_ID);

				ProcessVariableInstance[] pvs = (ProcessVariableInstance[]) rp.getResults();
				ProcessVariableInstance[] pvsToUpdate = getPvsToUpdate(pvs, targetPvNames);

                                NamedTypedValue valueAcp = new NamedTypedValue( convertXMLToTypeValue(xml,type));
                                if (valueAcp == null) {
                                        throw new IllegalArgumentException("Cannot update the value of PV \"" +
                                                        pvsToUpdate[0].getName() + "\", because no ACP was specified.");
                                }


                                updatePvValue(pvsToUpdate[0], operation, new ActivityClassParameter(valueAcp), ts);

				pes.setProcessVariables(processId, pvsToUpdate);
			}

		} catch (Exception e) {
			String message = "An error occurred while trying to update " + "the PVs of process with id " +
					processId + ": " + e.getMessage();
			
			throw new SmartServiceException(ErrorCode.GENERIC_RUNTIME_ERROR, e);
		}
	}

	@Override
	public void setDynamicInputs(List<? extends NamedTypedValue> inputs) {
		customInputs = inputs;
	}

	public Long getTypeIDByName(String typeName)
	{
		ServiceContext sc = ServiceContextFactory.getServiceContext(smartServiceCtx.getUsername());
		TypeService ts = ServiceLocator.getTypeService(sc);
                //Get Namespace and LocalPart
                String[] nsAndLocal = typeName.split("\\###");
                return ts.getTypeByQualifiedName(new QName(nsAndLocal[0],nsAndLocal[1])).getId();
                
	}

	public TypedValue convertXMLToTypeValue(String xml, String typeName) throws Exception
	{               
                //Replace the tags we don't want 
                xml = xml.replaceAll("/<n1:.* xmlns:n1=\".*\">/", "<root>");
                xml = xml.replaceAll("/<\\/n1:.*>/", "</root>");
                
                
                //Get an AppianObject for the CDT. We'll add the data from the XML to this.
 		ServiceContext sc = ServiceContextFactory.getServiceContext(smartServiceCtx.getUsername());              
                TypeService ts = ServiceLocator.getTypeService(sc);
                Long TypeID = getTypeIDByName(typeName);
                
                //Get the TypedValue for this type and convert to an AppianObject for processing
                TypedValue tv = new TypedValue(TypeID);
                AppianTypeFactory tf =  AppianTypeFactory.newInstance(ts);
                AppianElement ae = tf.toAppianElement(tv);
                
                
                //Create HashMap of primitives of different types. We'll use this to get the typeIds of the primitives for comparison
                HashMap<Long,String> types = new HashMap<Long,String>();
                types.put(tf.createBoolean(Boolean.FALSE).getTypeId(),"boolean");
                types.put(tf.createDate(new Date()).getTypeId(),"date");
                types.put(tf.createDouble(Double.NaN).getTypeId(),"double");
                types.put(tf.createLong(new Long(1)).getTypeId(),"long");
                types.put(tf.createDateTime(new Timestamp(1,1,1,1,1,1,1)).getTypeId(),"datetime");
                types.put(tf.createString("hi").getTypeId(),"string");
                types.put(tf.createTime(new Time(1,1,1)).getTypeId(),"time");
                              
                String type = "";
                
                //Check whether this is an AppianPrimitive, AppianList (of Primitives or CDTs)
                //Still not sure what AppianComplex represents, so ignoring for now
                if(ae instanceof AppianObject) //CDT
                {
                    //Convert from AppianObject to TypedValue
                    return tf.toTypedValue(handleCDT(ae,ts,TypeID,loadXMLFromString(xml),types,tf,(AppianObject) tf.toAppianElement(tv))); 
                }      
                else if(ae instanceof AppianPrimitive) //Primitive
                {
                    Document document=loadXMLFromString(xml);
                    NodeList nl = document.getElementsByTagName("Value");
                    String value = nl.item(0).getTextContent();
                    return tf.toTypedValue(handleAppianPrimitive(ae,tf,types,(loadXMLFromString(xml).getElementsByTagName("Value").item(0).getTextContent())));                  
                }
                else if(ae instanceof AppianList) //Array
                {
                    //need to check whether the array is of CDTs
                    //AppianElement listAE1 = ((AppianList) ae).get(0); //It'll be empty because it's not a real thing yet
                    
                    //Replace the TypeID with that of the single object
                    TypeID = getTypeIDByName(typeName.substring(0,typeName.length()-5));

                    AppianElement ae1 = tf.toAppianElement(new TypedValue(TypeID));
                    
                    if(ae1 instanceof AppianObject) //Array of CDTs
                    {
                        //Split up the XML into an array
                        //xml = xml.replace("/<\\/root>/","</root>%new%");
                        String[] xmlArray = xml.split("%new%");
                                                
                        AppianList al = tf.createList(TypeID);
                        for(int i = 0; i<xmlArray.length; i++)
                        {
                            al.add(handleCDT(ae1,ts,TypeID,loadXMLFromString(xmlArray[i]),types,tf,(AppianObject)ae1));
                        }
                        return tf.toTypedValue(al);
                    }
                    else if(ae1 instanceof AppianPrimitive) //Array of Primitives
                    {
                        //Move code for handling a primitive to a new method and call that for each entry
                        NodeList nl = loadXMLFromString(xml).getElementsByTagName("Value");
                        AppianList al = tf.createList(TypeID);
                        for(int i = 0;i<nl.getLength();i++)
                        {
                            al.add(handleAppianPrimitive(ae1,tf,types,nl.item(i).getTextContent()));
                        }
                        return tf.toTypedValue(al);
                    }
                    else
                    {
                        //Don't know if this is possible, but throw exception just in case!
                        throw new AppianException("This datatype "+typeName.substring(0,typeName.length()-5)+" is not supported. ae.toString()="+ae.toString()+", ae1.toString="+ae1.toString()+", typeid= "+TypeID);
                    }
                }

            return tv;
	}
        
        public AppianPrimitive handleAppianPrimitive(AppianElement ae, AppianTypeFactory tf, HashMap<Long,String> types, String value) throws ParseException, AppianException
        {
            //Work out the type of the Primitive, create a new instance, convert to a typed value
            type = types.get(((AppianPrimitive)ae).getTypeId());

            //Create a new AppianPrimitive for the value we want to update to 
            AppianPrimitive newPrim = null;         

            //Set the new AppianPrimitive                  
            if(null != type)
                 switch (type) {
                 case "string":
                     newPrim = tf.createString(value);
                     break;
                 case "boolean":
                     newPrim = tf.createBoolean(Boolean.parseBoolean(value));
                     break;
                 case "long":
                     newPrim = tf.createLong(Long.parseLong(value));
                     break;
                 case "double":
                     newPrim = tf.createDouble(Double.parseDouble(value));
                     break;
                 case "datetime":
                     newPrim = tf.createDateTime(new Timestamp((new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(value)).getTime()));
                     break;
                 case "date":
                     newPrim = tf.createDate(new SimpleDateFormat("yyyy-MM-dd").parse(value));
                     break;
                 case "time":
                     newPrim = tf.createTime((Time) new SimpleDateFormat("hh:mm:ss").parse(value));
                     break;
                 default:
                     break;
                 }
            if(newPrim == null)
            {
                throw new AppianException("value="+value+"; type="+type);
            }
            return newPrim;    
        }
        
        public AppianObject handleCDT(AppianElement ae, TypeService ts, Long typeID, Document document, HashMap<Long,String> types, AppianTypeFactory tf, AppianObject ao) throws InvalidTypeException, ParseException, AppianException
        {
            //Get the variables within the CDT
            NamedTypedValue[] instanceProps = ts.getInstanceProperties(typeID);

            //HashMap to store all of the CDT field names and their types
            HashMap<String,Long> cdtFields = new HashMap<>();

            //Get all the CDT field names and types
            for(int i = 0; i<instanceProps.length; i++)
            {
                cdtFields.put(instanceProps[i].getName(), instanceProps[i].getInstanceType()); 
            }

            //Iterate through cdtFields, get each type and its equivalent label, create an AppianPrimitive of that type and put into the AppianObject equivalent of the CDT
            Set<String> keys = cdtFields.keySet();
            NodeList nl = null;

            Iterator it = keys.iterator();

            while(it.hasNext())
            {
                nl = document.getElementsByTagName(it.next().toString());
                for(int i = 0;i<nl.getLength();i++)
                {
                    type = types.get(cdtFields.get( nl.item(i).getNodeName()));
                    
                    if(nl.item(i).getTextContent().isEmpty() || nl.item(i).getTextContent() == null)
                    {
                        ao.put(nl.item(i).getNodeName(), null);
                    }
                    else
                    {

                        if(null != type)
                            switch (type) {
                            case "string":
                                ao.put(nl.item(i).getNodeName(), tf.createString(nl.item(i).getTextContent()));
                                break;
                            case "boolean":
                                ao.put(nl.item(i).getNodeName(), tf.createBoolean(Boolean.parseBoolean(nl.item(i).getTextContent())));
                                break;
                            case "long":
                                ao.put(nl.item(i).getNodeName(), tf.createLong(Long.parseLong(nl.item(i).getTextContent())));
                                break;
                            case "double":
                                ao.put(nl.item(i).getNodeName(), tf.createDouble(Double.parseDouble(nl.item(i).getTextContent())));
                                break;
                            case "datetime":
                                ao.put(nl.item(i).getNodeName(), tf.createDateTime(new Timestamp((new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(nl.item(i).getTextContent())).getTime())));
                                break;
                            case "date":
                                ao.put(nl.item(i).getNodeName(), tf.createDate(new SimpleDateFormat("yyyy-MM-dd").parse(nl.item(i).getTextContent())));
                                break;
                            case "time":
                                ao.put(nl.item(i).getNodeName(), tf.createTime((Time) new SimpleDateFormat("hh:mm:ss").parse(nl.item(i).getTextContent())));
                                break;
                            default:
                                //If nothing is found it suggests there's a nested CDT or a list. Throw an exception.
                                throw new AppianException("The CDT contains a nested CDT or an array, which is not supported.");
                        }
                    }
                }
            }
            return ao;
        }

	public static Document loadXMLFromString(String xml) throws Exception
	{
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
	}

        private ProcessVariableInstance[] getPvsToUpdate(ProcessVariableInstance[] allPvs_,
			String[] targetPvNames_) throws IllegalArgumentException {

		ProcessVariableInstance[] pvsToUpdate = null;
		pvsToUpdate = new ProcessVariableInstance[targetPvNames_.length];
		for (int i = 0; i < targetPvNames_.length; ++i) {
			pvsToUpdate[i] = (ProcessVariableInstance) NamedTypedValue.findNtvByName(allPvs_,
					targetPvNames_[i]);
			if (pvsToUpdate[i] == null) {
				throw new IllegalArgumentException("Could not find PV: " + targetPvNames_[i]);
			}
		}
		return pvsToUpdate;
	}

	private void updatePvValue(ProcessVariableInstance pv_, String operation_,
			ActivityClassParameter valueAcp_, TypeService ts) throws InvalidTypeException {
		Long pvType = pv_.getInstanceType().longValue();
		Long acpValueType = valueAcp_.getInstanceType().longValue();
		boolean isCustomType = (pvType>AppianType.INITIAL_CUSTOM_TYPE) || (acpValueType>AppianType.INITIAL_CUSTOM_TYPE);
		boolean pvIsMultiple = ts.getDatatypeProperties(pvType).isListType();
		boolean acpValueIsMultiple = ts.getDatatypeProperties(acpValueType).isListType();

		//check if type is an Appian type
		
		
		//if it is Appian type, make sure the types match
		if(!isCustomType){

			if (pvType != acpValueType ||
					(pvIsMultiple != acpValueIsMultiple && !OPERATION_APPEND.equals(operation_))) {
				throw new IllegalArgumentException("The type (" + acpValueType +
						(acpValueIsMultiple ? ", multiple" : "") +
						") of the given value does not match the type of the target PV (" + pvType +
						(pvIsMultiple ? ", multiple" : "") + ").");
			}
		} 		

		if (OPERATION_SET.equals(operation_)) {
						
			//if types are CDTs (not Appian types) cast ACP type into PV type
			if(isCustomType){
				//if the types are different
				if (pvType.longValue() != acpValueType.longValue()){
					Object[] acpRunningValue = (Object[])valueAcp_.getValue();
					int pvValueSize = ts.getType(pv_.getInstanceType()).getInstanceProperties().length;
					Object[] pvRunningValue = new Object[pvValueSize];
					//copy only the old values from new type acp to old type pv
					for(int i=0; i<pvValueSize;i++){
						pvRunningValue[i]=acpRunningValue[i];
					}
					
					pv_.setRunningValue(pvRunningValue);
				}
				else {
					pv_.setRunningValue(valueAcp_.getValue());
				}
					
				//If type is a simple PV, set its value
			} else {
				pv_.setRunningValue(valueAcp_.getValue());
			}


		} else {
			throw new UnsupportedOperationException("The specified operation \"" + operation_ +
					"\" is not supported.");
		}
	}

	public SetExternalPVDynamic(SmartServiceContext smartServiceCtx) {
		super();
		this.smartServiceCtx = smartServiceCtx;
	}

	@Override
	public void onSave(MessageContainer messages) {
	}

	@Override
	public void validate(MessageContainer messages) {
	}

	@Input(required = Required.OPTIONAL)
	@Name("processId")
	public void setProcessId(Long val) {
		this.processId = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("targetPvName")
	public void setTargetPvNames(String[] val) {
		this.targetPvNames = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("operation")
	public void setOperation(String val) {
		this.operation = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("type")
	public void setPVType(String val) {
		this.type = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("xml")
	public void setXml(String val) {
		this.xml = val;
	}
	

}
