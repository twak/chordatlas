package org.twak.tweed.gen;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.tweed.Tweed;
import org.twak.utils.collections.Arrayz;
import org.twak.utils.geom.Line;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;

public abstract class LineGen extends Gen {

	protected String filename;
	Geometry geom;

	float height;
	
	public LineGen(String name, Tweed tweed) {
		super(name, tweed);
	}
	
	@Override
	public void calculate() {
		
		for (Spatial s : gNode.getChildren())
			s.removeFromParent();
		
		Mesh m = new Mesh();
	
		m.setMode(Mesh.Mode.Lines);
	
		List<Float> coords = new ArrayList();
		List<Integer> inds = new ArrayList();
		
		for ( Line l : getLines() ) {
			
				inds.add(inds.size());
				inds.add(inds.size());
				
				coords.add ( (float)l.start.x );
				coords.add (0f);
				coords.add( (float) l.start.y );
				
				coords.add ( (float)l.end.x );
				coords.add (0f);
				coords.add( (float) l.end.y );
		}
		
		m.setBuffer(VertexBuffer.Type.Position, 3, Arrayz.toFloatArray ( coords ) );
		m.setBuffer( VertexBuffer.Type.Index, 2, Arrayz.toIntArray(inds) );
		
		geom = new Geometry(filename, m);
		
		Material lineMaterial =  new Material(tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		lineMaterial.setColor("Color", new ColorRGBA( color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, 1f) );
		geom.setMaterial(lineMaterial);
		
		for (Spatial s : gNode.getChildren())
			s.removeFromParent();
		
		geom.setLocalTranslation(0, height * 30, 0);
		
		gNode.attachChild( geom );
		geom.updateModelBound();

		
		super.calculate();
		
	}

	public abstract Iterable<Line> getLines();
	
	protected void setHeight(float d) {
		
		tweed.enqueue(new Callable<Spatial>() {
			public Spatial call() throws Exception {

				LineGen.this.height = d;
				calculate();
				tweed.gainFocus();
				return geom;
			}

		});

	}

	int sliderValue = 0;
	
	@Override
	public JComponent getUI() {
		
		JPanel out = new JPanel(new BorderLayout());
		
		int MAX = 1000;
		final JSlider s = new JSlider(SwingConstants.VERTICAL, 0, MAX, sliderValue);
		
		s.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				sliderValue = s.getValue();
				setHeight( sliderValue / (float) MAX);
			}
		});
	
		out.add(s, BorderLayout.WEST);
		
		return s;
	}

}