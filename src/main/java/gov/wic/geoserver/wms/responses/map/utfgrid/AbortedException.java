package gov.wic.geoserver.wms.responses.map.utfgrid;

/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

/**
 * Based on work by Mauro Bartolomeoli.
 * 
 * @author Eric Capito
 */
public class AbortedException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6754341593037927621L;

	public AbortedException(String msg) {
		super(msg);
	}
}
