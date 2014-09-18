package gov.wic.geoserver.wms.responses.map.utfgrid;

import java.io.IOException;
import java.io.OutputStream;

import org.geoserver.ows.Response;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;

public class UTFGridResponse extends Response {
	public UTFGridResponse() {
		super(EncodeUTFGrid.class, UTFGridMapProducer.MIME_TYPE);
	}

	/**
	 * Writes the generated map to an OutputStream.
	 * 
	 * @param out
	 *            final output stream
	 * 
	 * @throws ServiceException
	 *             DOCUMENT ME!
	 * @throws IOException
	 *             DOCUMENT ME!
	 */
	@Override
	public void write(Object value, OutputStream output, Operation operation)
			throws IOException, ServiceException {
		// Assert.isInstanceOf(EncodeHTMLImageMap.class, value);
		EncodeUTFGrid htmlImageMapEncoder = (EncodeUTFGrid) value;
		try {
			htmlImageMapEncoder.encode(output);
		} finally {
			htmlImageMapEncoder.dispose();
		}
	}

	@Override
	public String getMimeType(Object value, Operation operation)
			throws ServiceException {
		return UTFGridMapProducer.MIME_TYPE;
	}
}
