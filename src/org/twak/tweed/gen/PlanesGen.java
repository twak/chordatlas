package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.tweed.tools.PlaneTool;
import org.twak.utils.ui.ListDownLayout;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

public class PlanesGen extends Gen implements ICanSave {

	public static class Plane {
		public Vector3f a, b;
		float heightMax = 30, heightMin = 0;
		public Color color;

		public Plane(){}
		public Plane (Color color) {
			this.color = color;
		}
		
		public Spatial render( Tweed tweed ) {

			
			if (a == null  || b == null)
				return null;
			
			Mesh mesh1 = new Mesh();
			{
				Vector3f[] vertices = new Vector3f[4];
				vertices[ 0 ] = new Vector3f( a.x, heightMin, a.z );
				vertices[ 1 ] = new Vector3f( a.x, heightMax, a.z );
				vertices[ 2 ] = new Vector3f( b.x, heightMin, b.z );
				vertices[ 3 ] = new Vector3f( b.x, heightMax, b.z );

				Vector3f[] texCoord = new Vector3f[4];
				texCoord[ 0 ] = new Vector3f( 0, 0, 0 );
				texCoord[ 1 ] = new Vector3f( 1, 0, 0 );
				texCoord[ 2 ] = new Vector3f( 0, 1, 0 );
				texCoord[ 3 ] = new Vector3f( 1, 1, 0 );

				int[] indexes = { 0, 1, 3, 3, 2, 0, 3, 1, 0, 0, 2, 3 };

				mesh1.setBuffer( VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer( vertices ) );

				mesh1.setBuffer( VertexBuffer.Type.TexCoord, 3, BufferUtils.createFloatBuffer( texCoord ) );
				mesh1.setBuffer( VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer( indexes ) );
				mesh1.updateBound();
			}
			Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
			mat.getAdditionalRenderState().setBlendMode( BlendMode.Alpha );
			mat.setColor( "Color", new ColorRGBA( color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 0.5f ) );

			//		Material mat = new Material(tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");

			Geometry spat = new Geometry( "Box", mesh1 );
			spat.setMaterial( mat );

			

			return spat;
		}
	}

	public List<Plane> planes = new ArrayList<>();

	public PlanesGen(){}
	public PlanesGen( Tweed tweed ) {
		super( "plane gen", tweed );
	}

	@Override
	public void calculate() {

		for ( Spatial s : gNode.getChildren() )
			s.removeFromParent();

		for ( Plane p : planes ) {

			
			p.color = color;
			
			Spatial s = p.render( tweed );

			if (s == null)
				continue;
			
			s.setUserData( ClickMe.class.getSimpleName(), new Object[] { new ClickMe() {
				@Override
				public void clicked( Object data ) {
					selected( p );
				}

			} } );
			gNode.attachChild( s );

		}

		super.calculate();
	}

	private void selected( Plane p ) {

		JPanel ui = new JPanel( new ListDownLayout() );
		
		JButton edit = new JButton( "edit" );
		edit.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				tweed.setTool( new PlaneTool( tweed, PlanesGen.this ) );
			}
		} );
		
		JButton delete = new JButton( "delete" );

		delete.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				planes.remove( p );
				PlanesGen.this.calculateOnJmeThread();
			}
		} );

		ui.add( edit );
		ui.add( delete );

		tweed.frame.setGenUI( ui );
	}

	@Override
	public JComponent getUI() {
		return new JLabel("Use select tool to delete");
	}
}
