package gov.wic.geoserver.wms.responses.map.utfgrid;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.geoserver.wms.GetMapOutputFormat;
import org.geoserver.wms.MapProducerCapabilities;
import org.geoserver.wms.WMSMapContent;
import org.vfny.geoserver.ServiceException;

public class UTFGridMapProducer implements GetMapOutputFormat {

	static final String MIME_TYPE = "application/json";

	static final MapProducerCapabilities CAPABILITIES = new MapProducerCapabilities(
			true, false, false, false, null);

	@Override
	public MapProducerCapabilities getCapabilities(String arg0) {
		return CAPABILITIES;
	}

	@Override
	public String getMimeType() {
		return MIME_TYPE;
	}

	@Override
	public Set<String> getOutputFormatNames() {
		return Collections.singleton(MIME_TYPE);
	}

	@Override
	public EncodeUTFGrid produceMap(WMSMapContent mapContent) throws ServiceException,
			IOException {
        return new EncodeUTFGrid(mapContent);
	}

}
