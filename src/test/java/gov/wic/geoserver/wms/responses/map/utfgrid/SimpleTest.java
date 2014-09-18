package gov.wic.geoserver.wms.responses.map.utfgrid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import gov.wic.geoserver.wms.responses.map.utfgrid.UTFGridMapTest.MyPropertyDataStore;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Logger;

import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.wms.WMSMapContent;
import org.geotools.data.DataStore;
import org.geotools.data.FeatureSource;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.referencing.CRS;
import org.geotools.styling.SLDParser;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.geotools.test.TestData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.vfny.geoserver.global.GeoserverDataDirectory;

public class SimpleTest {

	private static final StyleFactory sFac = CommonFactoryFinder
			.getStyleFactory(null);
	
	private static final Logger LOGGER = org.geotools.util.logging.Logging
			.getLogger(SimpleTest.class.getPackage().getName());
	
	private DataStore testDS = null;
	
	private UTFGridMapProducer mapProducer;
	private UTFGridResponse response;
	
	private CoordinateReferenceSystem WGS84;
	private int mapWidth = 100;
	private int mapHeight = 100;
	
	/*
	@Test
	public void testMapProduceBasicPolygons() throws Exception {

		final FeatureSource<SimpleFeatureType, SimpleFeature> fs = testDS
				.getFeatureSource("BasicPolygons");
		final ReferencedEnvelope env = new ReferencedEnvelope(fs.getBounds(),
				WGS84);

		LOGGER.info("about to create map ctx for BasicPolygons with bounds "
				+ env);

		final WMSMapContent map = new WMSMapContent();
		map.getViewport().setBounds(env);
		map.setMapWidth(mapWidth);
		map.setMapHeight(mapHeight);
		map.setTransparent(false);

		Style basicStyle = getTestStyle("default.sld");
		map.addLayer(new FeatureLayer(fs, basicStyle));

		EncodeUTFGrid result = mapProducer.produceMap(map);
		assertTestResult("BasicPolygons", result);

	}*/
	
	@Test
	public void testStates() throws Exception {
		File shapeFile = TestData.file(this, "featureTypes/states.shp");
		ShapefileDataStore ds = new ShapefileDataStore(shapeFile.toURL());

		final FeatureSource<SimpleFeatureType, SimpleFeature> fs = ds
				.getFeatureSource("states");
		final ReferencedEnvelope env = new ReferencedEnvelope(fs.getBounds(),
				WGS84);

		final WMSMapContent map = new WMSMapContent();
		map.getViewport().setBounds(env);
		map.setMapWidth(mapWidth);
		map.setMapHeight(mapHeight);
		map.setTransparent(false);

		Style basicStyle = getTestStyle("Population.sld");
		map.addLayer(new FeatureLayer(fs, basicStyle));

		EncodeUTFGrid imageMap = this.mapProducer.produceMap(map);

		assertTestResult("States", imageMap);
	}
	
	@Before
	public void setUp() throws Exception {
		// initializes GeoServer Resource Loading (is needed by some tests
		// to not produce
		// exceptions)

		System.setProperty("org.geotools.referencing.forceXY", "true");
		File testdata = TestData.file(this, ".");
		System.setProperty("GEOSERVER_DATA_DIR", testdata.getAbsolutePath());
		GeoServerResourceLoader loader = new GeoServerResourceLoader(testdata);
		GenericWebApplicationContext context = new GenericWebApplicationContext();
		context.getBeanFactory().registerSingleton("resourceLoader", loader);
		GeoserverDataDirectory.init(context);

		// initialized WGS84 CRS (used by many tests)
		WGS84 = CRS.decode("EPSG:4326");

		testDS = getTestDataStore();

		// initializes GetMapOutputFormat factory and actual producer
		// this.mapFactory = getProducerFactory();
		this.mapProducer = new UTFGridMapProducer();
		this.response = new UTFGridResponse();
	}
	
	public DataStore getTestDataStore() throws IOException {
		File testdata = TestData.file(this, "featureTypes");

		return new MyPropertyDataStore(testdata);

	}
	
	protected Style getTestStyle(String styleName) throws Exception {
		SLDParser parser = new SLDParser(sFac);
		File styleRes = TestData.file(this, "styles/" + styleName);

		parser.setInput(styleRes);

		Style s = parser.readXML()[0];

		return s;
	}

	@After
	public void tearDown() throws Exception {
		this.mapProducer = null;
		this.response = null;
	}
	
	protected void assertTestResult(String testName, EncodeUTFGrid imageMap)
			throws Exception {

		ByteArrayOutputStream out = null;
		StringBuffer testText = new StringBuffer();
		try {

			out = new ByteArrayOutputStream();
			this.response.write(imageMap, out, null);
			out.flush();
			out.close();
			File testFile = TestData.file(this, "results/" + testName + ".txt");
			BufferedReader reader = new BufferedReader(new FileReader(testFile));

			String s = null;
			while ((s = reader.readLine()) != null)
				testText.append(s + "\n");

			reader.close();

		} finally {
			imageMap.dispose();
		}
		assertNotNull(out);
		assertTrue(out.size() > 0);
		String s = new String(out.toByteArray());

		//LOGGER.info("result: "+s);
		assertEquals(testText.toString(), s);
	}
}
