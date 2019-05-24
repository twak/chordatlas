package org.twak.tweed.gen.skel;

import java.io.File;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.twak.camp.Corner;
import org.twak.camp.Edge;
import org.twak.camp.Output;
import org.twak.camp.Output.SharedEdge;
import org.twak.camp.ui.Bar;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.siteplan.campskeleton.PlanSkeleton.ColumnProperties;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.ICanSave;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjDump.Face;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.MiniFacade;

/**
 * Super hacky code to pull an obj into the Skelgen data-structure...
 */
public class ObjSkelGen extends SkelGen implements ICanSave {
	
	transient ObjDump obj; 
	String objFile;
	
	public ObjSkelGen(Tweed tweed, String name, String objFile ) {
		super(tweed);
		this.name = name;
		this.objFile = objFile;
		
		HalfMesh2 hm = new HalfMesh2();
		hm.faces.add( new SuperFace() );
		setRender( hm );
	}
	
	protected PlanSkeleton calc( SuperFace sf ) {

		if (obj == null)
			obj = new ObjDump( new File (Tweed.JME, objFile ) );
		
		PlanSkeleton skel = new PlanSkeleton();
		sf.skel = skel;
		skel.output = new Output( skel );
		
		sf.e = null;
		
		HalfMesh2.Builder planEdges = new HalfMesh2.Builder( SuperEdge.class, SuperFace.class );
		planEdges.newPoint( new Point2d() );
		
		for (Face of : obj.material2Face.valueList()) {
			
			org.twak.camp.Output.Face skelFace = skel.output.new Face();
			skelFace.points = new LoopL<>();
			
			Loop<Point3d> loop = skelFace.points.newLoop();
			Loop<SharedEdge> loopSE = new Loop<>();
			
			for (double[] pt : obj.getPoints( of ) )
				loop.append( new Point3d (pt[0], pt[2], pt[1]) );
			
			loop.reverse();
			
			double lowest = Double.MAX_VALUE;
			Corner lowestC = null;
			SharedEdge lowestSE = null;
			
			for (Loopable<Point3d> lp : loop.loopableIterator()) {
				
				Point3d a = lp.get(), b = lp.next.get();
				SharedEdge se = new SharedEdge( a, b );
				loopSE.append( se );
				se.right = se.left = skelFace;
						
				if ( a.z + b.z < lowest ) {
					lowest = a.z + b.z;
					lowestC = new Corner( a );
					skelFace.edge = new Edge( lowestC, new Corner ( b ) );
					lowestSE = se;
				}
			}
			
			ColumnProperties cp = new ColumnProperties( null, null, 0 ) ;
			Bar bar = new Bar(new Point2d(), new Point2d());
			bar.tags.add( new SETag( new SuperEdge(), sf ) );
			cp.defBar = bar;
			skel.columnProperties.put( skelFace.edge, cp);
			
			for (SharedEdge se : loopSE) {
				
				Point3d other = se.start;
				
				if (!other.equals( skelFace.edge.start ) && !other.equals( skelFace.edge.end ) ) {
					
					Point3d bottom = skelFace.edge.line().closestPointOn( other, false );
					Vector3d uphill = new Vector3d( other );
					uphill.sub( bottom );
					uphill.normalize();
					skelFace.edge.uphill = uphill;
					
					break;
				}
			}
			
			MiniFacade mf = new MiniFacade();
			if (Math.abs( skelFace.edge.uphill.y ) < 0.1 ) {
				mf.sf = sf;
				skelFace.profile.add( new WallTag(new WallTag(), mf) );
			}
			else
				skelFace.profile.add( new RoofTag() );
			
			sf.e = planEdges.newPoint( new Point2d() ); 
			((SuperEdge)sf.e).toEdit = mf; // we need each edge to reference the relevant mf :(
			
			skelFace.edges = loopSE.singleton();
			skelFace.definingSE.add( lowestSE );
			skel.output.faces.put( lowestC, skelFace  );
		}
		planEdges.newFace();
		
		
		sf.mr.setOutline( sf.skel.output );
		
		return skel;
	}
	
	@Override
	public JComponent getUI() {

		JPanel ui = new JPanel( new ListDownLayout() );
		
		JButton dec = new JButton( "edit material" );
		dec.addActionListener( l -> textureSelected( null, null, ObjSkelGen.this ) );
		ui.add( dec );
		
		return ui;
	}
	
}
