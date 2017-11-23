package org.twak.tweed.tools;

import java.awt.Color;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.Gen;
import org.twak.tweed.gen.PlanesGen;
import org.twak.tweed.gen.PlanesGen.Plane;

import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

public class PlaneTool extends Tool {

	Tweed tweed;
	PlanesGen pg = null;

	Plane plane;
	Spatial planeS;

	public PlaneTool( Tweed tweed ) {
		super( tweed );
		this.tweed = tweed;
	}

	@Override
	public void activate( Tweed tweed ) {
		this.tweed = tweed;

		List<Gen> gens = tweed.frame.gens( PlanesGen.class );
		if ( gens.isEmpty() ) {
			pg = new PlanesGen( tweed );
			tweed.frame.addGen( pg, true );
		} else
			pg = (PlanesGen) gens.get( 0 );

//		createPlane();
	}

	@Override
	public void deactivate() {

		

		if ( planeS != null )
			planeS.removeFromParent();
		
	}


	@Override
	public void clear() {
	}

	@Override
	public void clickedOn( Spatial target, Vector3f vector3f, Vector2f cursorPosition ) {
	}

	@Override
	public void dragStart( Geometry target, Vector2f screen, Vector3f d3 ) {
		plane = new Plane (Color.white);
		plane.a = new Vector3f( d3.x, 0, d3.z );
		plane.b = null;
	}

	@Override
	public void dragging( Vector2f screen, Vector3f ignore ) {

		Camera cam = tweed.getCamera();
		
		Vector3f dir = cam.getWorldCoordinates ( screen, 0 ), d3;
		dir.subtractLocal( cam.getLocation() );
		new Ray( cam.getLocation(), dir ).intersectsWherePlane( new com.jme3.math.Plane(Jme3z.UP, 0), d3 = new Vector3f() );
		
		plane.b = new Vector3f( d3.x, 0, d3.z );

		if ( planeS != null )
			planeS.removeFromParent();

		planeS = plane.render( tweed );
		
		if ( planeS != null )
			tweed.getRootNode().attachChild( planeS );

	}

	@Override
	public boolean isDragging() {

		return plane != null && plane.a != null;
	}

	@Override
	public void dragEnd() {

		
		if (plane.a != null && plane.b != null && plane.a.distanceSquared( plane.b ) > 4) {
			
			plane.color = pg.color;
			pg.planes.add(plane);
			pg.calculate();
		}

		plane = new Plane( Color.white );

		if ( planeS != null )
			planeS.removeFromParent();
	}

	@Override
	public String getName() {
		return "draw planes";
	}
	
	@Override
	public void getUI( JPanel genUI ) {
		genUI.add( new JLabel("planes filter the rendered panoramas") );
	}

}
