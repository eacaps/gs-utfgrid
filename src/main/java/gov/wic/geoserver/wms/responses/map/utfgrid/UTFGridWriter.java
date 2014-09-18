package gov.wic.geoserver.wms.responses.map.utfgrid;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.WMSMapContent;
import org.geotools.data.DataSourceException;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.renderer.lite.RendererUtilities;
import org.geotools.styling.FeatureTypeStyle;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class UTFGridWriter extends OutputStreamWriter {

	private static final Logger LOGGER = org.geotools.util.logging.Logging
			.getLogger(UTFGridWriter.class.getPackage().getName());

	GeometryFactory gFac = new GeometryFactory();

	WMSMapContent mapContent = null;

	/** rect representing screen coordinates space **/
	Rectangle mapArea = null;
	ReferencedEnvelope mapEnv = null;
	Polygon clippingBox = null;

	/**
	 * Transformation from layer (world) coordinates to "screen" coordinates.
	 */
	private AffineTransform worldToScreen = null;

	public UTFGridWriter(OutputStream out, WMSMapContent mapContent)
			throws UnsupportedEncodingException, ClassCastException {
		super(out, guessCharset(mapContent));

		this.mapContent = mapContent;
		mapEnv = mapContent.getRenderingArea();
		clippingBox = envToGeometry(mapEnv);
		mapArea = new Rectangle(mapContent.getMapWidth(),
				mapContent.getMapHeight());
		worldToScreen = RendererUtilities.worldToScreenTransform(mapEnv,
				mapArea);
	}

	private static String guessCharset(WMSMapContent mapContent) {
		GetMapRequest request = mapContent.getRequest();
		if (request != null && request.getRequestCharset() != null) {
			String requestCharset = request.getRequestCharset();
			return requestCharset;
		}
		return "UTF-8";
	}

	private Polygon envToGeometry(ReferencedEnvelope env) {

		Coordinate[] coordinates = new Coordinate[] {
				new Coordinate(env.getMinX(), env.getMinY()),
				new Coordinate(env.getMaxX(), env.getMinY()),
				new Coordinate(env.getMaxX(), env.getMaxY()),
				new Coordinate(env.getMinX(), env.getMaxY()),
				new Coordinate(env.getMinX(), env.getMinY()) };
		LinearRing bbox = gFac.createLinearRing(coordinates);
		return gFac.createPolygon(bbox, new LinearRing[] {});
	}

	/**
	 * private void initWriters() { writers = new
	 * HashMap<Class<?>,HTMLImageMapFeatureWriter>(); writers.put(Point.class,
	 * new PointWriter()); writers.put(LineString.class, new
	 * LineStringWriter()); writers.put(LinearRing.class, new
	 * LineStringWriter()); writers.put(Polygon.class, new PolygonWriter());
	 * writers.put(MultiPoint.class, new MultiPointWriter());
	 * writers.put(MultiLineString.class, new MultiLineStringWriter());
	 * writers.put(MultiPolygon.class, new MultiPolygonWriter());
	 * writers.put(GeometryCollection.class, new GeometryCollectionWriter()); }
	 */

	public void writeFeatures(SimpleFeatureCollection fColl,
			FeatureTypeStyle[] ftsList) throws IOException, AbortedException {

		HashMap<String, UTFEntry> entries = new HashMap<String, UTFEntry>();
		ArrayList<UTFEntry> orderedentrylist = new ArrayList<UTFEntry>();

		SimpleFeature ft;
		SimpleFeatureIterator iter = null;
		try {
			SimpleFeatureType featureType = fColl.getSchema();
			Class<?> gtype = featureType.getGeometryDescriptor().getType()
					.getBinding();

			// write the initial blank grid
			int linelength = this.mapArea.width / 2;
			int lines = this.mapArea.height / 2;
			ArrayList<String> lineslist = new ArrayList<String>();
			// this.write("[");
			for (int x = 0; x < lines; x++) {
				StringBuffer lb = new StringBuffer("");
				StringBuffer sb = new StringBuffer("");
				if (x != 0) {
				}
				for (int s = 0; s < linelength; s++) {
					sb.append(" ");
					lb.append(" ");
				}
				lineslist.add(lb.toString());
			}

			// iterates through the single features
			iter = fColl.features();
			while (iter.hasNext()) {
				ft = iter.next();
				Geometry geo = (Geometry) ft.getDefaultGeometry();

				if (!clippingBox.contains(geo)) {
					try {
						Geometry clippedGeometry = clippingBox
								.intersection(geo);
						ft.setDefaultGeometry(clippedGeometry);
					} catch (Throwable e) {
						// ignore and use the original geo
					}
				}
				ft = null;
			}
			Coordinate[] coordinates = clippingBox.getCoordinates();
			System.out.println(coordinates[0]);
			Coordinate bl = coordinates[0];
			Coordinate br = coordinates[1];
			Coordinate tr = coordinates[2];
			Coordinate tl = coordinates[3];
			double hdiff = tl.y - bl.y;
			double wdiff = tr.x - tl.x;
			double xstep = wdiff / ((double) linelength);
			double ystep = hdiff / ((double) lines);
			int val = 1;
			SimpleFeatureIterator iterator = fColl.features();
			//reversed loop order because the above call should only be done once.
			//TODO: might need to turn that into a separate iteration, but havent tested yet.
			while (iterator.hasNext()) {
				// for each line in the output
				for (int y = 0; y < lines; y++) {
					double yval = tl.y - (ystep * y);
					Coordinate left = new Coordinate(bl.x, yval);
					Coordinate right = new Coordinate(br.x, yval);
					Coordinate[] linecoords = { left, right };
					// create a line to run the intersection against
					LineString line = new GeometryFactory()
							.createLineString(linecoords);
					SimpleFeature sf = iterator.next();
					Geometry geom = (Geometry) sf.getDefaultGeometry();
					Geometry intersection = line.intersection(geom);
					if (!intersection.isEmpty()) {
						String id = sf.getID();
						UTFEntry entry = entries.get(id);
						if (entry == null) {
							entry = new UTFEntry(val++);
							HashMap<Object, Object> map = entry.getMap();
							Collection<Property> properties = sf
									.getProperties();
							for (Property prop : properties) {
								PropertyType type = prop.getType();
								Name name = prop.getName();
								String namestr = name.toString();
								Object valobj = prop.getValue();
								String valstr = valobj.toString();
								if (!(type instanceof GeometryType))
									map.put(namestr, valstr);
							}
							entries.put(id, entry);
							orderedentrylist.add(entry);
						}
						// System.out.println(intersection);
						// find x start
						if (MultiPolygon.class.equals(gtype)
								|| Polygon.class.equals(gtype)
								|| LinearRing.class.equals(gtype)) {
							Coordinate[] icoords = intersection
									.getCoordinates();
							int processedcoords = 0;
							while (processedcoords < icoords.length) {
								double startpos = 0;
								double endpos = -1;
								if (icoords.length - processedcoords > 1) {
									Coordinate ileft = icoords[processedcoords++];
									Coordinate iright = icoords[processedcoords++];
									double sdiff = ileft.x - left.x;
									startpos = sdiff / xstep;
									double ediff = iright.x - left.x;
									endpos = ediff / xstep;
								} else {
									Coordinate ileft = icoords[processedcoords++];
									double sdiff = ileft.x - left.x;
									startpos = sdiff / xstep;
									endpos = startpos;
								}
								// System.out.println("spos: "+startpos+" epos: "+endpos);
								String linestr = lineslist.get(y);
								char[] chars = linestr.toCharArray();
								for (int c = (int) startpos; c <= endpos; c++) {
									if (c < linelength)
										chars[c] = this.getUTFChar(entry
												.getVal());
								}
								linestr = new String(chars);
								lineslist.set(y, linestr);
							}
						//lines and points need some work, right now too resolution dependent
						} else if (LineString.class.equals(gtype)
								|| Point.class.equals(gtype)
								|| MultiLineString.class.equals(gtype)
								|| MultiPoint.class.equals(gtype)) {
							Coordinate[] icoords = intersection
									.getCoordinates();
							String linestr = lineslist.get(y);
							char[] chars = linestr.toCharArray();
							for (Coordinate coord : icoords) {
								double sdiff = coord.x - left.x;
								double pos = sdiff / xstep;
								if (pos < linelength) {
									chars[(int) pos] = this.getUTFChar(entry
											.getVal());
								}
							}
							linestr = new String(chars);
							lineslist.set(y, linestr);
						} else {
							// wtf mate
						}
						// System.out.println(linestr);
					}
				}
			}

			this.write("\"keys\":[\"\"");
			boolean first = true;
			StringBuffer kb = new StringBuffer("");
			StringBuffer db = new StringBuffer("");
			for (UTFEntry entry : orderedentrylist) {

				kb.append(",");
				if (!first) {
					db.append(",");
				}
				int ik = entry.getVal();
				kb.append("\"" + ik + "\"");
				db.append("\"" + ik + "\":{");
				HashMap<Object, Object> map = entry.getMap();
				boolean mfirst = true;
				for (Entry<Object, Object> mapentry : map.entrySet()) {
					if (!mfirst) {
						db.append(",");
					}
					Object mkey = mapentry.getKey();
					Object mval = mapentry.getValue();
					if (mkey != null && mval != null) {
						db.append("\"" + mkey + "\":");
						db.append("\"" + mval + "\"");
						mfirst = false;
					}
				}
				db.append("}");
				first = false;
			}
			this.write(kb.toString());
			this.write("],");
			this.write(" \"data\":{");
			this.write(db.toString());
			this.write("},");
			this.write("\"grid\":");
			this.write("[");
			for (int x = 0; x < lines; x++) {
				StringBuffer lb = new StringBuffer("");
				StringBuffer sb = new StringBuffer("");
				if (x != 0) {
					sb.append(",");
				}
				sb.append("\"");
				String line = lineslist.get(x);
				for (int s = 0; s < linelength; s++) {
					sb.append(line.charAt(s));
				}
				sb.append("\"");
				this.write(sb.toString());
			}
			this.write("]");

			LOGGER.fine("encoded " + featureType.getTypeName());
		} catch (NoSuchElementException ex) {
			throw new DataSourceException(ex.getMessage(), ex);
		} finally {
			if (iter != null) {
				// make sure we always close
				iter.close();
			}
		}

	}

	private char getUTFChar(int val) {
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
