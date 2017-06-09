package org.twak.tweed.gen;

import java.util.HashSet;
import java.util.Set;

import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
import org.twak.utils.Line;
import org.twak.utils.geom.Graph2D;

public abstract class Graph2DGen extends LineGen {

	Graph2D graph;

	public Graph2DGen(String name, Tweed tweed) {
		super(name, tweed);
	}
	
	public Graph2DGen(String name, Graph2D graph, Tweed tweed) {
		super(name, tweed);
		this.graph = graph;
	}

	public Iterable<Line> getLines() {
		
		Set<Line> out = new HashSet();
		for ( Point2d pt : graph.keySet() )
			for (Line l : graph.get(pt) ) 
				out.add(l);
		
		return out;
		
	}
}