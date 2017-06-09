package org.twak.viewTrace;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.geotools.gml.GMLFilterDocument;
import org.geotools.gml.GMLFilterGeometry;
import org.geotools.gml.GMLHandlerJTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeocentricCRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.utils.Line;
import org.twak.utils.Pair;
import org.twak.utils.collections.ConsecutivePairs;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.SuperLoop;
import org.twak.utils.geom.Graph2D;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.UnionWalker;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;

public abstract class GMLReader {
	
	public abstract void newPoly (String name) ;
	public abstract void addLine (double[] s, double[] e) ;
	public abstract void hole (int n) ;
	
	private CoordinateReferenceSystem targetCRS;
	private CoordinateReferenceSystem sourceCRS;
	
	public GMLReader (InputSource input) throws IOException, SAXException {
		this (input, null, null);
	}
	
	public GMLReader (InputSource input, CoordinateReferenceSystem target, CoordinateReferenceSystem sourceCS) throws IOException, SAXException {
		
		this.targetCRS = target;
		this.sourceCRS = sourceCS;
		
	    XMLReader reader = XMLReaderFactory.createXMLReader();
	    
	    Callback callback = new Callback();
	    GMLFilterGeometry geometryCallback = new GMLFilterGeometry( callback );	    
	    GMLFilterDocument gmlCallback = new GMLFilterDocument( geometryCallback );	    
		reader.setContentHandler( gmlCallback );
		
	    reader.parse(input);
	}
	
	class Callback extends DefaultHandler implements GMLHandlerJTS {

//		CoordinateReferenceSystem sourceCRS;
		MathTransform transform;
		int count = 0;
		
		String featureName = "none";
		
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
				throws SAXException {
			
			if (localName.equals("regent") ) 
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
//				sourceCRS = CRS.decode(TweedSettings.settings.gmlCoordSystem); // we should detect from the file?!
				if (sourceCRS != targetCRS)
					transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
			}
			catch (Throwable th) {
				th.printStackTrace();
			}
		}
		
		@Override
		public void geometry(Geometry arg0) {
			
			if (arg0 == null )
				return;
			
			newPoly(featureName);
			
			// TODO Auto-generated method stub
			if (count != 0) {
//				System.out.println(arg0.getGeometryType());
		
				try {
					
//					Point cen = arg0.getCentroid();
					
//					List<Coordinate> outerInners = new ArrayList();
					
					if ( arg0 instanceof com.vividsolutions.jts.geom.Polygon ) {

						com.vividsolutions.jts.geom.Polygon p = ( (com.vividsolutions.jts.geom.Polygon) arg0 );

						addCoords( p.getExteriorRing().getCoordinates(), transform );
						for ( int r = 0; r < p.getNumInteriorRing(); r++ ) {
							hole( r );
							addCoords( p.getInteriorRingN( r ).getCoordinates(), transform );
						}
					} else if ( arg0 instanceof com.vividsolutions.jts.geom.LineString ) {
						LineString ls = (LineString) arg0;
						addCoords( ls.getCoordinates(), transform );
					} else if ( arg0 instanceof MultiPolygon ) {
						MultiPolygon mp = (MultiPolygon) arg0;

						for ( int i = 0; i < mp.getNumGeometries(); i++ )
							addCoords( mp.getGeometryN( i ).getCoordinates(), transform );
					}
									
				} catch (Throwable e) {
					e.printStackTrace();
//					System.exit(0);
				}
			}
			count++;
			featureName = "?";
		}	
	}	
	
	private void addCoords (Coordinate coords[], MathTransform transform) throws Throwable {
		for (Pair<Coordinate, Coordinate> pair : new ConsecutivePairs<Coordinate>( Arrays.asList( coords ) , true)) {
			
			double 
				x1 = pair.first().x,// - cen.getCoordinate().x, 
				y1 = pair.first().y,// - cen.getCoordinate().y,
				x2 = pair.second().x,// - cen.getCoordinate().x, 
				y2 = pair.second().y;// - cen.getCoordinate().y;

			if (targetCRS != null && transform != null)
			{
				double[] result = new double[] {x1, y1, x2, y2, 0, 0};
				transform.transform( result, 0, result, 0, 2 );
				
				addLine (
						new double[] {result[0], result[1], result[2]}, 
						new double[] {result[3], result[4], result[5]}
						);
				
			}
			else {
				
				addLine(new double[] {x1, y1}, new double[] { x2, y2} );
			}
			
//			GML2Graph.this.result.newLine(new Point2d(x1, y1), new Point2d(x2, y2));
			
		}

	}
	
	public static void main1(String[] args) {
		Graph2D g2 = readGMLToGraph ( new File( "/home/twak/data/langham/langham.gml" ) );
		
		g2.removeInnerEdges();
		
		Point2d offset = new Point2d();
		
		int count = 0;
		
		for (Point2d p : g2.map.keySet())
			for (Line l : g2.get(p) ) {
				count ++;
				offset.add(l.start);
				System.out.println(l);
			}
		
		offset.scale(1./count);
		System.out.println("offset is " + offset);
		
		AffineTransform at = AffineTransform.getScaleInstance(-1, 1);
		at.concatenate(AffineTransform.getTranslateInstance(-offset.x, -offset.y));
		g2 = g2.apply( at );
		
		UnionWalker uw = new UnionWalker();
		
		for (Point2d a : g2.map.keySet()) {
			for (Line l : g2.get(a)) {
				uw.addEdge(l.start, l.end);
			}
		}
		
		LoopL<Point2d> out = uw.findAll();
		ObjDump obj = new ObjDump();
		
		for (Loop<Point2d> loop : out) {
			List<Point3d> pts = new ArrayList();
			for (Point2d pt : loop)
				pts.add(new Point3d(pt.x, 0, pt.y));
			
			obj.addFace(pts);
		}
		
		obj.dump(new File( Tweed.CONFIG + "langham.obj"));
	}
	
	public static Graph2D readGMLToGraph (File in) {
		
		try {
			
			InputSource input = new InputSource( new FileInputStream(in) );
			
			Graph2D out = new Graph2D();
			
			new GMLReader(input) {
				
				public void newPoly (String name) {}
				
				@Override
				public void hole(int n) {
					newPoly( "hoel" );
				}
				
				public void addLine (double[] s, double[] e) {
					out.add( new Point2d ( e ) , new Point2d ( s ) );
				}
			};
			
			return out;
			
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		}
	}

	public static LoopL<Point3d> readGML3d( File in, DefaultGeocentricCRS targetCS,  CoordinateReferenceSystem sourceCS ) {
		LoopL<Point3d> out = new LoopL();
		try {
			
			InputSource input = new InputSource( new FileInputStream(in) );
			
			new GMLReader(input, targetCS, sourceCS) {
			
				Loop<Point3d> parent, poly;
				
				public void newPoly (String name) {
					parent = poly = new SuperLoop<Point3d>(name);
					out.add(poly);
				}

				@Override
				public void hole(int n) {
					poly = new Loop<Point3d>();
					parent.holes.add(poly);
				}
				
				public void addLine (double[] s, double[] e) {
					poly.append(new Point3d( s[0], s[1], s[2] ));
				}
			};
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		System.out.println("found " + out.size()+" polygons ");
		
		return out;
	}	
}
