package org.twak.footprints;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;

import org.geotools.feature.FeatureCollection;
import org.geotools.feature.collection.AbstractFeatureVisitor;
import org.geotools.geometry.jts.JTS;
import org.geotools.gml.GMLFilterDocument;
import org.geotools.gml.GMLFilterGeometry;
import org.geotools.gml.GMLHandlerJTS;
import org.geotools.referencing.CRS;
import org.geotools.util.NullProgressListener;
import org.geotools.xml.handlers.xsi.ComplexContentHandler;
import org.opengis.feature.Feature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.twak.tweed.TweedSettings;
import org.twak.utils.ConsecutivePairs;
import org.twak.utils.Pair;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

public class Footprints {
	
	public static void main(String args[]) throws Exception {
//		StringReader reader = new StringReader(  );
//        InputSource input = new InputSource( new FileInputStream("/home/twak/Desktop/wgs84.gml") );
//        InputSource input = new InputSource( new FileInputStream("/home/twak/Downloads/Download_around_ucl_562795 (1)/mastermap-topo_1451134/mastermap-topo_1451134_0.gml") );
        
		InputSource input = new InputSource( new FileInputStream("/home/twak/data/Download_around_ucl_562795/buildings.gml") );
        Callback result = parse( input );
		

		
//		Matcher m = name.matcher( "<ogr:buildings fid=\"osgb1000001787210138\"> )");
//		if (m.matches()) {
//			System.out.println("matches " + m.group(1));
//		}
		
//		test();
	}
	
	public static Callback parse(InputSource input) throws IOException, SAXException {

	    // parse xml
	    XMLReader reader = XMLReaderFactory.createXMLReader();
	    
	    Callback callback = new Callback();
	    GMLFilterGeometry geometryCallback = new GMLFilterGeometry( callback );	    
	    GMLFilterDocument gmlCallback = new GMLFilterDocument( geometryCallback );	    
		reader.setContentHandler( gmlCallback );
		
		ComplexContentHandler ch;
		
	    reader.parse(input);
	    
	    return callback;
	}
	/**
	 * This class is called when the SAX parser has finished
	 * parsing a Filter.
	 */
	static class Callback extends DefaultHandler implements GMLHandlerJTS {

		CoordinateReferenceSystem sourceCRS, targetCRS;
		MathTransform transform;
		int count = 0;
		
		String featureName = "none";
		
		private final static Pattern name = Pattern.compile(".*buildings\\ fid=\\\"([^\\\"]*)\\\".*", Pattern.DOTALL);
		
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
				throws SAXException {
			
			if (localName.equals("buildings") ) 
				featureName = attributes.getValue("fid");
			
			super.startElement(uri, localName, qName, attributes);
		}
		
		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if (localName.equals("buildings") ) {
				featureName = null;
			}
			super.endElement(uri, localName, qName);
		}
		
		public Callback() {
			try {
				sourceCRS = CRS.decode( TweedSettings.settings.gmlCoordSystem );
				targetCRS = CRS.decode("EPSG:4326"); // lat long
				transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
			}
			catch (Throwable th) {
				th.printStackTrace();
			}
		}
		
		@Override
		public void geometry(Geometry arg0) {
			
			if (arg0 == null || arg0.getArea() < 80)
				return;
			
			// TODO Auto-generated method stub
			if (count != 0) {
				System.out.println(arg0.getGeometryType());
		
				try {
					
					Point cen = arg0.getCentroid();
					Coordinate latLong = new Coordinate();
					
					JTS.transform(cen.getCoordinate(), latLong, transform);
					
					System.out.println(" ************************* " + count + " " + featureName);
					System.out.println("in EPSG:27700 " + cen);
					System.out.println("lat long " + latLong);

					URL url = new URL("https://maps.googleapis.com/maps/api/staticmap?center="+latLong.x+","+latLong.y+"&zoom=20&size=640x640&maptype=satellite&format=png32&key=AIzaSyDYAQH5nMlF0vEfdIg0seTiGUIcRbLNeI4");
					URLConnection connection = url.openConnection();
					InputStream is = connection.getInputStream();
//					
					BufferedImage image = ImageIO.read(is);// new BufferedImage( 640,640, BufferedImage.TYPE_3BYTE_BGR );
//					BufferedImage image = new BufferedImage( 640,640, BufferedImage.TYPE_3BYTE_BGR );
					Graphics2D g2 = (Graphics2D) image.getGraphics();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					
					double scale = 11f;

					int imageCenX = image.getWidth () >> 1,
						imageCenY = image.getHeight() >> 1;
					
							
					g2.setColor(Color.red);
					g2.setStroke(new BasicStroke(2f));
					
					for (Pair<Coordinate, Coordinate> pair : new ConsecutivePairs<Coordinate>( Arrays.asList( arg0.getCoordinates() ), true)) {
						
						double 
							x1 = pair.first().x - cen.getCoordinate().x, 
							y1 = pair.first().y - cen.getCoordinate().y,
							x2 = pair.second().x - cen.getCoordinate().x, 
							y2 = pair.second().y - cen.getCoordinate().y;

						x1 *= scale; x2 *= scale; y1 *= scale; y2 *= scale;
							
						g2.draw( new Line2D.Double(x1 + imageCenX, - y1 + imageCenY, x2 + imageCenX, - y2 +imageCenY) );
						
					}
					
					
					g2.drawString( HeightsToRedis.getHeight(featureName) +"m below roof", 5, 15 );
					g2.drawString( HeightsToRedis.getRoof(featureName) +"m including roof", 5, 30 );
					g2.drawString( latLong.x + ", " + latLong.y + " location ", 5, 45 );
					
					
					g2.dispose();
					
					
					
					ImageIO.write(image, "png", new FileOutputStream ( String.format( "/home/twak/data/footprints/center%04d.png", count )) );
					
					is.close();
					
					if (count > 1000)
						System.exit(0);
					
					
				} catch (Throwable e) {
					e.printStackTrace();
					System.exit(0);
				}
			}
			count++;
			featureName = "?";
		}	
	}	
	

	public static void test() throws IOException, SAXException, ParserConfigurationException {
		//create the parser with the gml 2.0 configuration
		org.geotools.xml.Configuration configuration = new org.geotools.gml2.GMLConfiguration();
		org.geotools.xml.Parser parser = new org.geotools.xml.Parser( configuration );
		
		InputStream xml = new FileInputStream("/home/twak/data/around_ucl_buildings.gml");

				//parse
				FeatureCollection fc = (FeatureCollection) parser.parse( xml );

				fc.accepts( new AbstractFeatureVisitor(){
				      public void visit( Feature feature ) {
				    	  
				    	  System.out.println(feature);
				    	  
//				          SimpleFeature f = (Feature) i.next();
//
//				          Point point = (Point) f.getDefaultGeometry();
//				          String name = (String) f.getAttribute( "name" );
				      }
				  }, new NullProgressListener() );

	}
	
	
}
