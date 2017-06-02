package org.twak.tweed.tools;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.text.NumberFormatter;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.Gen;
import org.twak.tweed.gen.MiniGen;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.Rainbow;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Cylinder;

public class AlignTool extends Tool {

	boolean dragging = false;
	
	long lastClick  = 0;
	
	Node markers = new Node();
	
	MiniGen toAlign = null;
	Vector3f[] alignMarkers = new Vector3f[2],
			otherMarkers = new Vector3f[2];
	
	boolean write = false; //write coords to disk
	
	double vOffset = 0;
	
	public AlignTool( Tweed tweed) {
		super (tweed);
	}

	@Override
	public void activate( Tweed tweedApp ) {
		super.activate( tweedApp );
		
		this.tweed = tweedApp;
		
		tweedApp.getRootNode().attachChild(markers);
		
	}
	
	@Override
	public void deactivate() {
		super.deactivate();
		
		showMarkers();
		 
		markers.removeFromParent();
	}
	
	
	@Override
	public void clickedOn( Spatial target, Vector3f loc, Vector2f cursorPosition )  {
		
		if (System.currentTimeMillis() - lastClick > 500) {
			
			System.out.println( target + " " +loc );
			
			Vector3f locs[];
			Object[] gens = target.getUserData( Gen.class.getSimpleName() );
			
			if (gens != null && gens[0] instanceof MiniGen) {
					locs = alignMarkers;
					toAlign = (MiniGen) gens[0];
					
					System.out.println("loc1 "+loc);
					
					toAlign.gNode.getLocalTransform().transformInverseVector( loc, loc );
//					loc = m.mult(loc);
					
					System.out.println("loc2 "+loc);
			}
			else
				locs = otherMarkers;
			
			int toMove = -1;
			
			for (int i = 0; i < locs.length; i++) {
				if (locs[i] == null) {
					toMove = i;
					break;
				}
				if (toMove == -1 || locs[i].distance(loc) < locs[toMove].distance( loc ) )
					toMove = i;
			}
			
			locs[toMove] = loc;

			
			if (
					alignMarkers[0] != null && alignMarkers[1] != null && 
					otherMarkers[0] != null && otherMarkers[1] != null    )
				doAlign();
			
			showMarkers();
			
			lastClick = System.currentTimeMillis();
		}
		
	}
	
	private void doAlign() {
		
		if (alignMarkers[0] == null || alignMarkers[1] == null || otherMarkers[0] == null || otherMarkers[1] == null) {
			JOptionPane.showMessageDialog( null, "click meshes to create align markers" );
			return;
		}
		
		Matrix4d toOrigin = buildFrame( alignMarkers );
		toOrigin.invert();
		
		Matrix4d o = buildFrame( otherMarkers );
		toOrigin.mul( o, toOrigin );

		toOrigin.m13 += vOffset;
		
		Transform t = new Transform();
		t.fromTransformMatrix( Jme3z.toJme ( toOrigin ) );
		
		toAlign.moveTo( t, write );
		
//		System.out.println ( "bounds "+ toAlign.gNode.getWorldBound() );
		
	}

	private Matrix4d buildFrame( Vector3f[] locs ) {
		
		Vector3f dir0 = new Vector3f(locs[1]);
		dir0 = dir0.subtract(locs[0]);
		dir0.y = 0;
//		dir.normalize();
		
		Vector3f dir1 = new Vector3f(0,dir0.length(),0);
		Vector3f dir2 = new Vector3f(-dir0.z, 0, dir0.x);
		
		Matrix4d out = new Matrix4d();
		out.setRow( 0, toArray( dir2 ));
		out.setRow( 1, toArray( dir1 ));
		out.setRow( 2, toArray( dir0 ));
		
		out.m03 = locs[0].x;
		out.m13 = 0;
		out.m23 = locs[0].z;
		out.m33 = 1;

		if (false)
		{
			Point3d a = new Point3d (0,0,0);
			Point3d b = new Point3d (0,0,1);
			
			out.transform( a );
			out.transform( b );
			
			System.out.println( a + " >>><<< " + b );
			System.out.println( locs[0] + " <<<>>> " + locs[1] );
		}
		
		return out;
	}
	
	private double[] toArray (Vector3f v) {
		return new double[] {v.x, v.y, v.z, 0};
	}
	

	private void showMarkers() {

		tweed.enqueue( new Runnable() { // run after toAlign's local transofrm has been updated!
			@Override
			public void run() {
		
		for ( Spatial s : markers.getChildren() )
			s.removeFromParent();

		
		Vector3f[] targetMarkers;
		if ( toAlign != null ) {
			Transform toTarget = toAlign.gNode.getLocalTransform();

			targetMarkers = new Vector3f[alignMarkers.length];

			for ( int i = 0; i < alignMarkers.length; i++ ) {
				if ( alignMarkers[ i ] != null ) {
					targetMarkers[ i ] = new Vector3f();
					toTarget.transformVector( alignMarkers[ i ], targetMarkers[ i ] );
				}
			}
		} else
			targetMarkers = new Vector3f[2];
		
		int cc = 4;
		for ( Vector3f[] a : new Vector3f[][] { otherMarkers, targetMarkers } ) {
			
			Color c = Rainbow.getColour( cc++ );
			
			for ( Vector3f v : a ) {
				if ( v != null ) {

					Cylinder handleOne = new Cylinder( 2, 3, 0.05f, 500f, true );
					
					handleOne.setMode( Mode.Lines );
					
					Geometry g1 = new Geometry( "h1", handleOne );
					
					Material mat1 = new Material( tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
					mat1.setColor( "Color", new ColorRGBA (c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1f ) ) ;
					g1.setMaterial( mat1 );
					Vector3f pos = new Vector3f( v );
					pos = pos.add( 0, 250, 0 );
					g1.setLocalTranslation( pos );
					g1.setLocalRotation( new Quaternion( new float[] {FastMath.PI/2, 0, 0} ) );
					markers.attachChild(g1);
				}

			}
		}
			}
		});

	}
	
	@Override
	public void dragStart( Geometry target, Vector2f cursorPosition ) {
		dragging = true;
	}
	
	@Override
	public void dragEnd() {
		dragging = false;
	}
	
	@Override
	public boolean isDragging() {
		return dragging;
	}
	
	@Override
	public String getName() {
		return "align";
	}
	
	@Override
	public void getUI( JPanel p ) {
		
		p.setLayout( new ListDownLayout() );
		
		JCheckBox w = new JCheckBox( "write", write );
		w.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				write = w.isSelected();
			}
		} );
		
		JButton swap = new JButton("swap");
		swap.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				Vector3f tmp = alignMarkers[0];
				alignMarkers[0] = alignMarkers[1];
				alignMarkers[1] = tmp;
				
				tweed.enqueue( new Runnable() {
					
					@Override
					public void run() {
						doAlign();
					}
				} );
			}
		} );
		
		JFormattedTextField tf = new JFormattedTextField( new NumberFormatter() );
		tf.setValue( vOffset );
		tf.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				vOffset = ((Number)tf.getValue()).doubleValue();
				doAlign();
			}
		} );
		
		p.add(w);
		p.add(swap);
		p.add(tf);
		
	}
}
