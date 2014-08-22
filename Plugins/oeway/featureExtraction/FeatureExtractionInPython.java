package plugins.oeway.featureExtraction;

import icy.file.FileUtil;
import icy.system.IcyHandledException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

import javax.script.ScriptException;

import org.apache.poi.util.IOUtils;
import org.python.core.PyObject;

import plugins.adufour.ezplug.EzVar;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarListener;
import plugins.adufour.ezplug.EzVarText;


public class FeatureExtractionInPython extends featureExtractionPlugin {
	Processor myProcessor;
	VarPythonScript inputScript;
	String lastScript="";
	HashMap<String,Object> options= null;
	EzVarText templateVar;
	EzVarText interpreterVar;
	String template = "";
	HashMap<String,String> library;
	String lastSaveContent= "";
	public void writeFile(String path, String content)
	{
		BufferedWriter writer = null;
        try {
            //create a temporary file

            File outputFile = new File(path);
            writer = new BufferedWriter(new FileWriter(outputFile));
			writer.write(content);
        } catch (Exception e1) {
            e1.printStackTrace();
        } finally {
            try {
                // Close the writer regardless of what happens...
                writer.close();
            } catch (Exception e1) {
            }
        }
	}
	public String readFile(String filename)
	{
	   String content = null;
	   File file = new File(filename); 
	   try {
	       FileReader reader = new FileReader(file);
	       char[] chars = new char[(int) file.length()];
	       reader.read(chars);
	       content = new String(chars);
	       reader.close();
	   } catch (IOException e) {
		   System.out.println("file not found:"+filename);
	   }
	   return content;
	}
	public String readFromJARFile(String filename)
			throws IOException
			{

			  InputStream is = getClass().getResourceAsStream(filename);
			  InputStreamReader isr = new InputStreamReader(is);
			  BufferedReader br = new BufferedReader(isr);
			  StringBuffer sb = new StringBuffer();
			  String line;
			  while ((line = br.readLine()) != null) 
			  {
			    sb.append(line+"\n");
			  }
			  br.close();
			  isr.close();
			  is.close();
			  return sb.toString();
			}
	@Override
	public void initialize(HashMap<String,Object> options, ArrayList<Object> optionUI) {
		try {
			template = readFromJARFile("CPython_ExenetTemplate.txt");
		} catch (IOException e) {
			template = readFile(FileUtil.getApplicationDirectory()+FileUtil.separator+"scripts"+FileUtil.separator+"CPython_ExenetTemplate.txt");
			e.printStackTrace();
		}
		
		final EzVarPythonScript ezps = new EzVarPythonScript("Script",
	            "from java.lang import Math\n" +
	            "import copy\n" +
	            "from org.python.modules import jarray\n\n\n" +
	            "def process(input, position):\n" +
	            "\t'''\n" +
	            "\tinput: 1d array\n" +
	            "\tposition: 1d array indicate current position, format: [x,y,z,t,c]\n" +
	            "\t'''\n\n"+
	            "\toutput=copy.deepcopy(input)\n\n" +
	            "\t#do something here\n\n" +
	            "\treturn output");
		templateVar = new EzVarText("Library", new String[]{}, 0, false);
		
		inputScript = (VarPythonScript) ezps.getVariable();
		inputScript.engine.put("options", options);


		lastSaveContent = readFile(FileUtil.getApplicationDirectory()+FileUtil.separator+"scripts"+FileUtil.separator+"auto_save.txt");
		inputScript.setValue(lastSaveContent);

		this.options = options;
		
		library= new HashMap<String,String>();
		library.put("auto-save","");
		library.put("default","");
		File folder = new File(FileUtil.getApplicationDirectory()+FileUtil.separator+"scripts");
		if(folder.exists())
		{
			for(File f:folder.listFiles())
			{
				if(f.isFile())
				{
					if(f.getName().toLowerCase().endsWith(".py"))
						library.put(f.getName().replace(".py", ""),f.getAbsolutePath());
				}
			}

		}
		else
		{
			FileUtil.createDir(folder);
		}
		

		templateVar.setToolTipText("Save your customized file in 'ICY_ROOT/scripts' folder, "+
				"naming it start with '"+"Jython"+"_' and end with '.py', "+
				"then it will appear in the library");
		
		EzVarListener<String> listener= new EzVarListener<String>(){

			@Override
			public void variableChanged(EzVar<String> source, String newValue) {
				try
				{
					
					if(templateVar.getValue().equals("default"))
					{
						if(interpreterVar.getValue().equals("CPython"))
						{
							String code = "import numpy as np\n"+
										"def process(input, position):\n"+
										"\toutput = input\n"+
										"\t#do something here\n\n" +
										"\treturn output\n";
							inputScript.setValue(code);
						}
						else
							inputScript.setValue(inputScript.getDefaultValue());
					}
					else if(templateVar.getValue().equals("auto-save"))
					{
						inputScript.setValue(lastSaveContent);
					}
					else
						if(library.containsKey(templateVar.getValue()))
					{
						String scriptString = readFile(library.get(templateVar.getValue()));
						inputScript.setValue(scriptString);
					}
				}
				catch(Exception e)
				{	
					
				}
				
			}
			
		};
		
		EzVarListener<String> listener2= new EzVarListener<String>(){

			@Override
			public void variableChanged(EzVar<String> source, String newValue) {
				try
				{
					String content = inputScript.getValue();
					String lastLib = templateVar.getValue();
					HashMap<String,String> library_tmp = new HashMap<String,String>();
					
					for(String s:library.keySet())
					{
						if(s.contains(interpreterVar.getValue()))
						{
							library_tmp.put(s, library.get(s));
						}
					}
					library_tmp.put("auto-save","");
					library_tmp.put("default","");
					String[] libs = new String[library_tmp.size()];
					int i =0;
					for(String s:library_tmp.keySet())
					{
						libs[i++] = s;
					}
					templateVar.setDefaultValues(libs, 0, false);
					
					templateVar.setToolTipText("Save your customized file in 'ICY_ROOT/scripts' folder, "+
								"naming it start with '"+interpreterVar.getValue()+"_' and end with '.py', "+
								"then it will appear in the library");
					if(!lastLib.equals("default"))
						inputScript.setValue(content);
					
					if(lastLib.equals("auto-save")||lastLib.equals("default"))
						templateVar.setValue(lastLib);
					else
					{
						templateVar.setValue("default");
						if(interpreterVar.getValue().equals("CPython"))
						{
							String code = "import numpy as np\n"+
										"def process(input, position):\n"+
										"\toutput = input\n"+
										"\t#do something here\n\n" +
										"\treturn output\n";
							inputScript.setValue(code);
						}
						else
							inputScript.setValue(inputScript.getDefaultValue());
					}
							

				}
				catch(Exception e)
				{	
					
				}
				
			}
			
		};
		
		interpreterVar = new EzVarText("Python Interpreter", new String[]{"CPython","Jython"},false);
		interpreterVar.addVarChangeListener(listener2);
		


		interpreterVar.setValue("Jython");
		
		HashMap<String,String> library_tmp = new HashMap<String,String>();
		for(String s:library.keySet())
		{
			if(s.contains(interpreterVar.getValue()))
			{
				library_tmp.put(s, library.get(s));
			}
		}
		library_tmp.put("auto-save","");
		library_tmp.put("default","");
		String[] libs = new String[library_tmp.size()];
		int i =0;
		for(String s:library_tmp.keySet())
		{
			libs[i++] = s;
		}
		
		templateVar.setDefaultValues(libs, 0, false);
		templateVar.setValue("auto-save");
		templateVar.addVarChangeListener(listener);
		
		optionUI.add(interpreterVar);
		optionUI.add(templateVar);
		optionUI.add(ezps);
	}
	public String packExecnetCode(String code)
	{

		
		String retString  = "code=\'\'\'\n";
		retString+= code;
		retString+= "\n\'\'\'\n";
		retString+= template;
		return retString;
	}
    interface Processor {
        double[] process(double[] input, double[] position);
    }
    public void compile()
    {
    	try
    	{

    		writeFile(FileUtil.getApplicationDirectory()+FileUtil.separator+"scripts"+FileUtil.separator+"auto_save.txt",inputScript.getValue());
    		lastSaveContent = inputScript.getValue();
	      	try
	        {
	      		inputScript.engine.clear();
	      		inputScript.engine.put("options", options);
	      		if(interpreterVar.getValue().equals("CPython"))
	      		{
	      			inputScript.evaluate(packExecnetCode(inputScript.getValue()));
	      		}
	      		else
	      			inputScript.evaluate();
	      		
	        }
	        catch (ScriptException e)
	        {
	            throw new IcyHandledException(e.getMessage());
	        }
	        PyObject resObject =  inputScript.engine.getPythonInterpreter().get("process");
	
	        myProcessor = (Processor)resObject.__tojava__(Processor.class);
    	}
    	catch(Exception e2)
    	{
    		e2.printStackTrace();
    	}
        
    }

	@Override
	public double[] process(double[] input, double[] position) {
		if(input !=null && input.length==0)
			return input;
		if(lastScript.hashCode()!=inputScript.getValue().hashCode())
		{
			compile();
			lastScript = inputScript.getValue();
		}
		return myProcessor.process(input,position);
	}

}
