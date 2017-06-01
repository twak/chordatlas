package org.twak.tweed.gen;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point3d;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.ClickMe;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.utils.Arrayz;
import org.twak.utils.Loop;
import org.twak.utils.ObjDump;
import org.twak.utils.Rainbow;
import org.twak.utils.geom.Line3d;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer;

public abstract class LineGen3d extends Gen implements IDumpObjs{

	protected String filename;

	public LineGen3d(String name, Tweed tweed) {
		super(name, tweed);
	}
	
	@Override
	public void calculate() {
		
		for (Spatial s : gNode.getChildren())
			s.removeFromParent();
		
		{
			Geometry geom;
			Mesh m = new Mesh();

			m.setMode( Mesh.Mode.Lines );

			List<Float> coords = new ArrayList();
			List<Integer> inds = new ArrayList();

			for ( Line3d l : getLines() ) {

				inds.add( inds.size() );
				inds.add( inds.size() );

				coords.add( (float) l.start.x );
				coords.add( (float) l.start.y );
				coords.add( (float) l.start.z );

				coords.add( (float) l.end.x );
				coords.add( (float) l.end.y );
				coords.add( (float) l.end.z );
			}

			m.setBuffer( VertexBuffer.Type.Position, 3, Arrayz.toFloatArray( coords ) );
			m.setBuffer( VertexBuffer.Type.Index, 2, Arrayz.toIntArray( inds ) );

			geom = new Geometry( filename, m );
			geom.setCullHint(  CullHint.Never );

			Material lineMaterial = new Material( tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
			lineMaterial.setColor( "Color", new ColorRGBA( color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1f ) );
			geom.setMaterial( lineMaterial );

			geom.setLocalTranslation( 0, 0, 0 );

			gNode.attachChild( geom );
		}
		
		int c = 0;
		{
			Geometry geom;
			Random randy = new Random();
			for ( Map.Entry<Loop<Point3d>, Integer> e : getFaces().entrySet() ) {

					Loop<Point3d> p = e.getKey();

					final int callbackI = e.getValue();

					Mesh m = Jme3z.fromLoop(p);

//					m.setMode( Mesh.Mode.Triangles );
//
//					List<Integer> inds = new ArrayList<>();
//					List<Float> pos = new ArrayList<>();
//					List<Float> norms = new ArrayList<>();
//					
//					Loopz.triangulate( p, true, inds, pos, norms );
//
//					m.set setVe( Type.Index, Arrayz.toIntArray(inds));
					
					
					geom = new Geometry( filename, m );

					geom.setCullHint( CullHint.Never );

//					ColorRGBA col = Jme3z.toJme( Rainbow.getColour( c++ ) );

					
					ColorRGBA col = new ColorRGBA( 
							color.getRed  () * randy.nextFloat() / 500f + 0.1f, 
							color.getGreen() * randy.nextFloat() / 500f + 0.1f, 
							color.getBlue () * randy.nextFloat() / 500f + 0.1f, 1f );
					
					Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
					
					mat.setColor( "Diffuse", col );
					mat.setColor( "Ambient", col );
					
					mat.setBoolean( "UseMaterialColors", true );
					
					geom.setUserData( ClickMe.class.getSimpleName(), new Object[] { new ClickMe() {
						@Override
						public void clicked( Object data ) {
							polyClicked( callbackI );
						}
					} } );
					
					geom.setUserData( Gen.class.getSimpleName(), new Object[] { this } );

					geom.setMaterial( mat );

					geom.setLocalTranslation( 0, 0, 0 );

					gNode.attachChild( geom );
				
			}
		}
		
		
		gNode.updateModelBound();

		
		super.calculate();
		
	}
	
	protected void polyClicked( int callbackI ) {
		
	}

	public abstract Iterable<Line3d> getLines();
	

	int sliderValue = 0;
	
	@Override
	public JComponent getUI() {
		
		JPanel out = new JPanel(new BorderLayout());
		
		return out;
	}

	public Map<Loop<Point3d>, Integer> getFaces() {
		return null;
	}
	
	@Override
	public void dumpObj( ObjDump dump ) {
		
		Jme3z.dump( dump, gNode, 0 );
		
	}

}