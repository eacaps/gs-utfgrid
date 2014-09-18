package gov.wic.geoserver.wms.responses.map.utfgrid;

import java.util.HashMap;

public class UTFEntry {

	public int val;
	public HashMap<Object,Object> map;
	
	public UTFEntry(int val) {
		this.val = val;
		this.map = new HashMap<Object,Object>();
	}
	
	public int getVal() {
		return val;
	}
	public HashMap<Object, Object> getMap() {
		return map;
	}
	
	
}
