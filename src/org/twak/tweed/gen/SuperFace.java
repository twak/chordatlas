package org.twak.tweed.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.viewTrace.facades.Appearance;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;

public class SuperFace extends HalfFace implements HasApp {

	public List<float[]> colors = null;
	public float[] roofColor;
	
	public List<Double> heights = new ArrayList(); // sampled from mesh
	public List<Double> maxProfHeights; // from profiles
	public double height = 4;
	public int classification;
	
	public Appearance app = new Appearance();
	
	public SuperFace() {
		super(null);
	}
	
	public SuperFace( HalfEdge e ) {
		super( e );
	}

	public SuperFace( SuperFace o ) {
		
		super (o.e);
		
		this.roofColor = o.roofColor;
		
		if (o.colors != null)
			this.colors = new ArrayList (o.colors);
		if (o.maxProfHeights!= null)
			this.maxProfHeights = new ArrayList (o.maxProfHeights);
		if (o.heights != null)
			this.heights = new ArrayList (o.heights);
		this.height = o.height;
		
		this.classification = o.classification;
	}

	public void mergeFrom( SuperFace o ) {
		
		if (o.colors != null) {
			
			if (colors == null)
				colors = new ArrayList<>();
			
			colors.addAll( o.colors );
		}
		
		if (o.maxProfHeights != null) {
			
			if (maxProfHeights== null)
				maxProfHeights = new ArrayList<>();
			
			this.maxProfHeights.addAll( o.maxProfHeights );
		}
		
		this.height = 0.5 * ( o.height + height );
		
		if (o.heights != null) {
			
			if (heights == null)
				heights = new ArrayList<>();
			
			heights.addAll(o.heights);
			
			updateHeight ();
		}
	}

	public void updateHeight() {
		if (heights != null && !heights.isEmpty()) {
			Collections.sort(heights);
			height = heights.get( heights.size() / 2 );
		}
	}

	public MiniFacade findMiniFacade() {
		
		for (HalfEdge e : this) {
			SuperEdge se = (SuperEdge)e;
			if (se.mini != null && !se.mini.isEmpty())
				return se.mini.get( 0 );
		}
		
		return null;
	}
}