package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.Callable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.tweed.handles.HandleMe;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjRead;
import org.twak.utils.ui.ListDownLayout;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Transform;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class ObjGen extends Gen implements IDumpObjs {

	protected String filename;

	boolean renderLines = false;
	float transparency = 1;

	transient Geometry geometry;
	
	boolean hasTextures = false;
	
	public ObjGen() {}
	public ObjGen(String filename, Tweed tweed) {
		super("obj " + new File(filename).getName(), tweed);
		this.filename = filename;
	}

	@Override
	public void calculate() {

		tweed.clearCurrentToolState();
		
		gNode.removeFromParent();
		
		Transform oldTransform = null;
		for (Spatial s : gNode.getChildren()) {
			s.removeFromParent();
			oldTransform = s.getLocalTransform();
		}

		Spatial mesh = tweed.getAssetManager().loadModel(filename);
		
		Material mat = null;
		
		ColorRGBA genCol = new ColorRGBA( 
				color.getRed()  /255f, 
				color.getGreen()/255f, 
				color.getBlue() /255f, transparency );
		
		genCol.multLocal( 0.35f );// addLocal( ColorRGBA.Black.mult( 0.5f ) );
		
		if (transparency == 0) { 
			mat = new Material(tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
			
			ColorRGBA c = Jme3z.toJme( color );
			
			mat.setColor( "Diffuse", c );
			mat.setColor( "Ambient", c.mult( 0.1f ) );
			
			mat.setBoolean( "UseMaterialColors", true );

		}
		else if (transparency == 1)
			mat = null;
		else {
			mat = new Material(tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
			mat.getAdditionalRenderState().setBlendMode(BlendMode.PremultAlpha);
			mat.setColor("Color", genCol );
		}
		
		Mode mode = renderLines ? Mesh.Mode.Lines : Mesh.Mode.Triangles;
		
		if (mesh instanceof Geometry) { // objs with several objects are a node
			
			geometry = (Geometry)mesh;
			setTexture (geometry, mat);
			geometry.getMesh().setMode( mode );
		} else {
			
			mesh.setUserData( HandleMe.class.getSimpleName(), true );
			for (Spatial s : ((Node)mesh).getChildren()) 
				((Geometry)s).getMesh().setMode( mode );
		}

		hasTextures = mat == null;
		if (mat != null) 
			mesh.setMaterial(mat);
			

		if (oldTransform != null)
			mesh.setLocalTransform( oldTransform );

		gNode.setUserData( HandleMe.class.getSimpleName(), true );
		
		gNode.attachChild(mesh);

		super.calculate();

	}

	protected void setTexture(Geometry g, Material mat) {}

	private final static int MAX = 1000;

	@Override
	public JComponent getUI() {

		JPanel out = new JPanel(new ListDownLayout());

		final JCheckBox renderLines = new JCheckBox("wireframe");
		renderLines.setSelected(this.renderLines);
		
		renderLines.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {

				tweed.enqueue(new Callable<Spatial>() {
					public Spatial call() throws Exception {
						ObjGen.this.renderLines = renderLines.isSelected();
						calculate();
						return null;
					}
				});
			}
		});

		final JSlider renderTransparent = new JSlider(0, MAX, (int) (MAX * transparency));
		renderTransparent.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				tweed.enqueue(new Callable<Spatial>() {
					public Spatial call() throws Exception {
						ObjGen.this.transparency = (renderTransparent.getValue() / (float) MAX);
						calculate();
						return null;
					}
				});
			}
		});

		out.add(renderLines);
		out.add(renderTransparent);

		return out;
	}

	@Override
	public void dumpObj( ObjDump dump ) {
		dump.setCurrentMaterial( Color.pink, 0.5);
		dump.addAll (new ObjRead( getFile() ));
	}

	public File getFile() {
		return new File (Tweed.JME + filename );
	}

}
