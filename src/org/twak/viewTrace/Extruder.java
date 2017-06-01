package org.twak.viewTrace;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point2d;
import javax.vecmath.Point2f;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.batik.parser.ParseException;
import org.apache.batik.parser.PathHandler;
import org.apache.batik.parser.PathParser;
import org.twak.utils.ConsecutivePairs;
import org.twak.utils.ObjDump;
import org.twak.utils.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class Extruder {

	public List<Point2d> path = new ArrayList();
	
	public Extruder(List<Point2d> path, double scale, boolean doCen) {
		
	}
	public Extruder(String name, double scale, boolean doCen) {

		readPath(name);

		Point2d cen = new Point2d();
		
		for (Point2d pt : path) {
			pt.x *= scale;
			pt.y *= scale;
			cen.add(pt);
		}
		
		if (doCen) {
			cen.scale(1f / path.size());
			System.out.println("offset is "+cen);
			
			for (Point2d pt : path) {
				pt.sub(cen);
			}
		}
		
		float height = 5;
		float totalLength = 0;
		for (Pair<Point2d, Point2d> pair : new ConsecutivePairs<>(path, false)) 
			totalLength += pair.first().distance(pair.second());
		
		ObjDump out = new ObjDump();
		
		double[][] 
				verts = new double[4][3],
				uvs   = new double[4][2];
		
		float lenSoFar = 0;
		
		for (Pair<Point2d, Point2d> pair : new ConsecutivePairs<>(path, false)) {

			double length = pair.first().distance(pair.second());
			
			set ( verts[0], 0, pair.first() );
			set ( verts[1], 0, pair.second() );
			set ( verts[2], height, pair.second() );
			set ( verts[3], height, pair.first() );
			
			set ( uvs[0],   lenSoFar / totalLength, 0  );
			set ( uvs[1], ( lenSoFar + length ) / totalLength, 0  );
			set ( uvs[2], ( lenSoFar + length ) / totalLength, 1  );
			set ( uvs[3],   lenSoFar / totalLength, 1  );
			
			out.addFace( verts, uvs, null );
			
			lenSoFar += length;
		}
		
		out.dump(new File("/home/twak/data/ludgate/ludgate.obj"));
	}

	private static void set(double[] fs, double u, double v) {
		fs[0] = u;
		fs[1] = v;
	}

	private static void set(double[] fs, double h, Point2d a) {
		fs[0] = a.x;
		fs[1] = h;
		fs[2] = a.y;
		
	}

	private void readPath(String name) {

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		try {

			// Using factory get an instance of document builder
			DocumentBuilder db = dbf.newDocumentBuilder();

			// parse using builder to get DOM representation of the XML file
			Document dom = db.parse(name);

			NodeList paths = dom.getElementsByTagName("path");

			PathParser pathParser = new PathParser();
			PathHandler pathHandler = new MyPathHandler();
			pathParser.setPathHandler(pathHandler);

			for (int i = 0; i < paths.getLength(); i++) {
				pathParser.parse(paths.item(i).getAttributes().getNamedItem("d").getTextContent());
			}
			
			for (Point2d pt : path) {
				System.out.println(pt);
			}

		} catch (Throwable pce) {
			pce.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new Extruder("/home/twak/data/ludgate/line_facade.svg", 0.05, true );
	}

	private class MyPathHandler implements PathHandler {

		Point2d last;
		
		@Override
		public void arcAbs(float arg0, float arg1, float arg2, boolean arg3, boolean arg4, float arg5, float arg6)
				throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void arcRel(float arg0, float arg1, float arg2, boolean arg3, boolean arg4, float arg5, float arg6)
				throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void closePath() throws ParseException {

		}

		@Override
		public void curvetoCubicAbs(float arg0, float arg1, float arg2, float arg3, float arg4, float arg5)
				throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void curvetoCubicRel(float arg0, float arg1, float arg2, float arg3, float arg4, float arg5)
				throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void curvetoCubicSmoothAbs(float arg0, float arg1, float arg2, float arg3) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void curvetoCubicSmoothRel(float arg0, float arg1, float arg2, float arg3) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void curvetoQuadraticAbs(float arg0, float arg1, float arg2, float arg3) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void curvetoQuadraticRel(float arg0, float arg1, float arg2, float arg3) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void curvetoQuadraticSmoothAbs(float arg0, float arg1) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void curvetoQuadraticSmoothRel(float arg0, float arg1) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void endPath() throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void linetoAbs(float arg0, float arg1) throws ParseException {
			path.add(new Point2d (arg0, arg1));

		}

		@Override
		public void linetoHorizontalAbs(float arg0) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void linetoHorizontalRel(float arg0) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void linetoRel(float arg0, float arg1) throws ParseException {
			path.add( last = new Point2d (arg0 + last.x, arg1 + last.y));
		}

		@Override
		public void linetoVerticalAbs(float arg0) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void linetoVerticalRel(float arg0) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void movetoAbs(float arg0, float arg1) throws ParseException {
			// TODO Auto-generated method stub

		}

		@Override
		public void movetoRel(float arg0, float arg1) throws ParseException {
			path.add( last = new Point2d (arg0, arg1));
		}

		@Override
		public void startPath() throws ParseException {
			// TODO Auto-generated method stub

		}
	}

}
