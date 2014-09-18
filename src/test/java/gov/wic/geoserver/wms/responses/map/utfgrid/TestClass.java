package gov.wic.geoserver.wms.responses.map.utfgrid;

import org.apache.batik.util.io.UTF8Decoder;

public class TestClass {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		/*int c = 0x122;
		String s = String.valueOf((char)c);
		System.out.println(s);
		c = 0x123;
		s = String.valueOf((char)c);
		System.out.println(s);*/
		
		int j=0;
		   

		   for(int i=32; i<=131;i++)
		    {
		   
			   char c = (char)i;
		     System.out.print((int)c + ":"  + c +"   ");
		  
		     
		     j++;

		    if(j>10)
		     {
		      System.out.println();
		      j=0;
		      }
		   }
		      System.out.println();
		      
		      for(int i=1; i<=131;i++)
			    {
		    	  System.out.println(i+":"+getUTFChar(i));
			    }
		
	}
	
	private static char getUTFChar(int val) {
		int charval = val + 32;
		/* 34 => " */
		if (charval >= 34) {
			charval = charval + 1;
		}
		/* 92 => \ */
		if (charval >= 92) {
			charval = charval + 1;
		}
		return (char) charval;
	}

}
