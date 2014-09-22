package gov.wic.geoserver.wms.responses.map.utfgrid;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.geoserver.wms.WMSMapContent;
import org.geoserver.wms.WebMap;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultQuery;
import org.geotools.data.Query;
import org.geotools.data.crs.ReprojectFeatureResults;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.GeoTools;
import org.geotools.feature.FeatureTypes;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.Layer;
import org.geotools.referencing.CRS;
import org.geotools.renderer.lite.RendererUtilities;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Rule;
import org.geotools.styling.Style;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.spatial.BBOX;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

public class EncodeUTFGrid extends WebMap {

	private static final Logger LOGGER = org.geotools.util.logging.Logging
			.getLogger("gov.wic.geoserver.wms.responses.map.utfgrid");

	/** Filter factory for creating filters */
	private final static FilterFactory filterFactory = CommonFactoryFinder
			.getFilterFactory2(GeoTools.getDefaultHints());

	/**
	 * Current writer. The writer is able to encode a single feature.
	 */
	private UTFGridWriter writer;

	private final int maxFilterSize = 15;

	public EncodeUTFGrid(WMSMapContent context) {
		super(context);
	}

	public void encode(final OutputStream out) throws IOException {
		// initializes the writer
		this.writer = new UTFGridWriter(out, mapContent);

		long t = System.currentTimeMillis();

		try {
			// encodes the different layers
			writeLayers();

			this.writer.flush();
			t = System.currentTimeMillis() - t;
			LOGGER.info("HTML ImageMap generated in " + t + " ms");
		} catch (AbortedException ex) {
			return;
		}
	}

	/**
	 * Applies Filters from style rules to the given query, to optimize
	 * DataStore queries. Similar to the method in StreamingRenderer.
	 * 
	 * @param styles
	 * @param q
	 */
	private Filter processRuleForQuery(FeatureTypeStyle[] styles) {
		try {

			// first we check to see if there are >
			// "getMaxFiltersToSendToDatastore" rules
			// if so, then we dont do anything since no matter what there's too
			// many to send down.
			// next we check for any else rules. If we find any --> dont send
			// anything to Datastore
			// next we check for rules w/o filters. If we find any --> dont send
			// anything to Datastore
			//
			// otherwise, we're gold and can "or" together all the fiters then
			// AND it with the original filter.
			// ie. SELECT * FROM ... WHERE (the_geom && BBOX) AND (filter1 OR
			// filter2 OR filter3);

			final List<Filter> filtersToDS = new ArrayList<Filter>();

			final int stylesLength = styles.length;

			int styleRulesLength;
			FeatureTypeStyle style;
			int u = 0;
			Rule r;

			for (int t = 0; t < stylesLength; t++) // look at each
			// featuretypestyle
			{
				style = styles[t];

				Rule[] rules = style.getRules();
				styleRulesLength = rules.length;

				for (u = 0; u < styleRulesLength; u++) // look at each
														// rule in the
														// featuretypestyle
				{
					r = rules[u];
					if (r.getFilter() == null)
						return null; // uh-oh has no filter (want all rows)
					if (r.hasElseFilter())
						return null; // uh-oh has elseRule
					filtersToDS.add(r.getFilter());
				}
			}

			Filter ruleFiltersCombined = null;
			Filter newFilter;
			// We're GOLD -- OR together all the Rule's Filters
			if (filtersToDS.size() == 1) // special case of 1 filter
			{
				ruleFiltersCombined = filtersToDS.get(0);
				// OR all filters if they are under maxFilterSize in number,
				// else, do not filter
			} else if (filtersToDS.size() < maxFilterSize) {
				// build it up
				ruleFiltersCombined = filtersToDS.get(0);
				final int size = filtersToDS.size();
				for (int t = 1; t < size; t++) // NOTE: dont
				// redo 1st one
				{
					newFilter = filtersToDS.get(t);
					ruleFiltersCombined = filterFactory.or(ruleFiltersCombined,
							newFilter);
				}
			}
			return ruleFiltersCombined;
			/*
			 * // combine with the geometry filter (preexisting)
			 * ruleFiltersCombined = filterFactory.or( q.getFilter(),
			 * ruleFiltersCombined);
			 * 
			 * // set the actual filter q.setFilter(ruleFiltersCombined);
			 */
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Filters the feature type styles of <code>style</code> returning only
	 * those that apply to <code>featureType</code>
	 * <p>
	 * This methods returns feature types for which
	 * <code>featureTypeStyle.getFeatureTypeName()</code> matches the name of
	 * the feature type of <code>featureType</code>, or matches the name of any
	 * parent type of the feature type of <code>featureType</code>. This method
	 * returns an empty array in the case of which no rules match.
	 * </p>
	 * 
	 * @param style
	 *            The style containing the feature type styles.
	 * @param featureType
	 *            The feature type being filtered against.
	 * 
	 */
	protected FeatureTypeStyle[] filterFeatureTypeStyles(Style style,
			SimpleFeatureType featureType) {
		FeatureTypeStyle[] featureTypeStyles = style.getFeatureTypeStyles();

		if ((featureTypeStyles == null) || (featureTypeStyles.length == 0)) {
			return new FeatureTypeStyle[0];
		}

		List<FeatureTypeStyle> filtered = new ArrayList<FeatureTypeStyle>(
				featureTypeStyles.length);

		for (int i = 0; i < featureTypeStyles.length; i++) {
			FeatureTypeStyle featureTypeStyle = featureTypeStyles[i];
			String featureTypeName = featureTypeStyle.getFeatureTypeName();
			Rule[] rules = featureTypeStyle.getRules();
			if (rules != null)
				rules = filterRules(rules);
			// does this style have any rules
			if (rules == null || rules.length == 0) {
				continue;
			}
			featureTypeStyle.setRules(rules);

			// does this style apply to the feature collection
			if (featureType.getTypeName().equalsIgnoreCase(featureTypeName)
					|| FeatureTypes.isDecendedFrom(featureType, null,
							featureTypeName)) {
				filtered.add(featureTypeStyle);
			}
		}

		return filtered.toArray(new FeatureTypeStyle[filtered.size()]);
	}

	/**
	 * Evaluates if the supplied scaleDenominator is congruent with a rule
	 * defined scale range.
	 * 
	 * @param r
	 *            current rule
	 * @param scaleDenominator
	 *            current value to verify
	 * @return true if scaleDenominator is in the rule defined range
	 */
	public static boolean isWithInScale(Rule r, double scaleDenominator) {
		return ((r.getMinScaleDenominator()) <= scaleDenominator)
				&& ((r.getMaxScaleDenominator()) > scaleDenominator);
	}

	/**
	 * Filter given rules, to consider only the rules compatible with the
	 * current scale.
	 * 
	 * @param rules
	 * @return
	 */
	private Rule[] filterRules(Rule[] rules) {
		List<Rule> result = new ArrayList<Rule>();
		for (int count = 0; count < rules.length; count++) {
			Rule rule = rules[count];
			double scaleDenominator;
			try {
				scaleDenominator = RendererUtilities
						.calculateScale(mapContent.getRenderingArea(),
								mapContent.getMapWidth(),
								mapContent.getMapHeight(), 90);

				// is this rule within scale?
				if (EncodeUTFGrid.isWithInScale(rule, scaleDenominator)) {
					result.add(rule);
				}
			} catch (TransformException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (FactoryException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// TODO Auto-generated method stub
		return result.toArray(new Rule[result.size()]);
	}

	/**
	 * Encodes the current set of layers.
	 * 
	 * @throws IOException
	 *             if an error occurs during encoding
	 * @throws AbortedException
	 *             if the encoding is aborted
	 * 
	 * @task TODO: respect layer filtering given by their Styles
	 */
	@SuppressWarnings("unchecked")
	private void writeLayers() throws IOException, AbortedException {
		for (Layer layer : mapContent.layers()) {
			SimpleFeatureSource fSource;
			fSource = (SimpleFeatureSource) layer.getFeatureSource();
			SimpleFeatureType schema = fSource.getSchema();
			/*
			 * FeatureSource fSource = layer.getFeatureSource(); FeatureType
			 * schema = fSource.getSchema();
			 */

			try {
				ReferencedEnvelope aoi = mapContent.getRenderingArea();

				CoordinateReferenceSystem sourceCrs = schema
						.getGeometryDescriptor().getCoordinateReferenceSystem();

				boolean reproject = (sourceCrs != null)
						&& !CRS.equalsIgnoreMetadata(
								aoi.getCoordinateReferenceSystem(), sourceCrs);
				if (reproject) {
					aoi = aoi.transform(sourceCrs, true);
				}
				// apply filters.
				// 1) bbox filter
				BBOX bboxFilter = filterFactory.bbox(schema
						.getGeometryDescriptor().getLocalName(), aoi.getMinX(),
						aoi.getMinY(), aoi.getMaxX(), aoi.getMaxY(), null);
				Query q = new Query(schema.getTypeName(), bboxFilter);

				String mapId = null;

				mapId = schema.getTypeName();

				// writer.write("{'keys':['14'], 'data': {'14': {'NAME': 'United States Virgin Islands', 'POP2005': 111408}}, , 'grid': ['                                                                ', '                                                                ', '                                                                ', '                                                                ', '                                                                ', '                                                                ', '                                                 !!!!!!!        ', '                                    ######     !!!!!!!!!        ', '                                   #######     !!!!!!!!!!       ', '                                 ########## !!!!!!!!!!!! !      ', '                                ##########!!!!!!!!!!!!!!!!!     ', '                              # ##########!!!!!!!!!!!!!!!!!!    ', '                               ##########!!!!!!!!!!!!!!!!!!!    ', '                              ##########!!!!!!!!!!!!!!!!!!!     ', '                            # ##########!!!!!!!!!!!!!!!!!!!     ', '                             ##########  !!!!!!!!!!!!!!!!!      ', '                          ###  #######  !!!!!!!!!!!!!!!!!!      ', '                        ## ###########!!!!!!!!!!!!!!!!!!!!      ', '                       #### ###  #####!!!!!!!!!!!!!!!!!!!!!     ', '                      ###     # ##### !!!!!!!!!!!!!!!!!!!!      ', '                     ### #  #########  !!!!!!!!!!!!!!!!!!!      ', '                    #############  ##   !!!!!!!!!!!!!!!!!!      ', '                      ##############       !!!!!!!!!!!!!!!      ', '                       ##    ########      !!!!!!!!!!!!!!!      ', '                   ####     #####  #       !!!!!!!!!!!!!!       ', '                    ####### ##########      !!!!!!!!!!!!!       ', '                   ######## ### ######      !!!!!!!!!!!!!       ', ' $      %           ######## ##########     !!!!!!!!!!!!!    &  ', '       %%%%%      #   ####### ## ########    !!!!!!!!!!!!       ', '      %%%%%%%%######## ########## #######   !!!!!!!!!!!!        ', '     %%%%%%%%%###########################    !!!!!!!!!!         ', '      %%%%%%%%###################### ######  !!!!!!!!           ', '     %%%%%%%%%######################  #####  !!!!!!!   '''''    ', '     %%%%%%%%%##################### #######  !!!!!!    '''''    ', '   %  %%%%%%%%############################   !!!!!      '''     ', '     %%%%%%%%%##################  # ######    !!!!           (  ', '     %%%%%%%%%#################     #### #    !!!!              ', '   %%%%%%%%%%%%################     #### #     !!!             )', '      %%%%%    %%###############    #######                   ))', '   %    %%%     %%###############  ########                  )))', '      %%        %%%#########################                 *))', '     %           %################# #########               *)))', '%%%%             ############################               **))', '                  ###########################                 ))', '                   #%%%%%%%%%%%%##############                +,', '                    %%%%%%%%%%%%%%%####%%## -#                 +', '                    %%%%%%%%%%%%%%%##%%%%##                     ', '                    %%%%%%%%%%%%%%%#%%%%                     ...', '                    %%%%%%%%%%%%%%%%%%%%                     /..', '                    %%%%%%%%%%%%%%%%%%                /      ...', '                     %%%%%%%%%%%%%%%%%                        ..', '                     %%%%%%%%%%%%%%%%                        000', '                       1%%%%%%%%%%%%                         000', '                      11111%%%%%%%%%                       .0022', '   %                   111111%     %3                      44422', '                        111111     %33                    445566', '       %%                 1111  1177778                   445566', '         %             1   1111111 9:%;<=>                555556', '   ?                         111@AA       B            CC DD5556', '                               1@EEF   G  HI              DDD66J', '                                  E  FKKKKK               LMM6JJ', '                                  NOOFKKKKKP               QQRSS', '       T                             FFFKKKPUV               RSS', '        T                           WFFFKKXPUVX                 ']}");
				// writer.write("<map name=\"" + mapId + "\">\n");

				// 2) definition query filter
				Query definitionQuery = layer.getQuery();
				LOGGER.info("Definition Query: " + definitionQuery.toString());
				if (!definitionQuery.equals(Query.ALL)) {
					if (q.equals(Query.ALL)) {
						q = (Query) definitionQuery;
					} else {
						q = (Query) DataUtilities.mixQueries(definitionQuery,
								q, "HTMLImageMapEncoder");
					}
				}

				FeatureTypeStyle[] ftsList = filterFeatureTypeStyles(
						layer.getStyle(), fSource.getSchema());
				// 3) rule filters
				Filter ruleFilter = processRuleForQuery(ftsList);
				if (ruleFilter != null) {
					// combine with the geometry filter (preexisting)
					ruleFilter = filterFactory.and(q.getFilter(), ruleFilter);

					// set the actual filter
					// q.setFilter(ruleFilter);
					q = new DefaultQuery(schema.getTypeName(), ruleFilter);
					// q = (Query) DataUtilities.mixQueries(new
					// Query(schema.getTypeName(),ruleFilter), q,
					// "HTMLImageMapEncoder");
				}
				// ensure reprojection occurs, do not trust query, use the
				// wrapper
				SimpleFeatureCollection fColl = null;// fSource.getFeatures(q);
				// FeatureCollection fColl=null;
				if (reproject) {
					fColl = new ReprojectFeatureResults(fSource.getFeatures(q),
							mapContent.getCoordinateReferenceSystem());
				} else
					fColl = fSource.getFeatures(q);

				// encodes the current layer, using the defined style

				writer.write("{");// 'keys':['14'], 'data': {'14': {'NAME':
									// 'United States Virgin Islands',
									// 'POP2005': 111408}}, , 'grid':");
				long t0 = System.currentTimeMillis();
				writer.writeFeatures(fColl, ftsList);
System.out.println("elapsed: "+(System.currentTimeMillis() - t0));
				writer.write("}");
				// writer.write("</map>\n");

			} catch (IOException ex) {
				throw ex;
			} catch (AbortedException ae) {
				LOGGER.info("process aborted: " + ae.getMessage());
				throw ae;
			} catch (Throwable t) {
				LOGGER.warning("UNCAUGHT exception: " + t.getMessage());

				IOException ioe = new IOException("UNCAUGHT exception: "
						+ t.getMessage());
				ioe.setStackTrace(t.getStackTrace());
				throw ioe;
			}
		}
	}
}
