package org.twak.mmg.functions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.twak.mmg.Command;
import org.twak.mmg.FollowOnMO;
import org.twak.mmg.Function;
import org.twak.mmg.InputSet;
import org.twak.mmg.MMG;
import org.twak.mmg.Node;
import org.twak.mmg.Reference;
import org.twak.mmg.MMG.Frame;
import org.twak.mmg.MOgram;
import org.twak.mmg.prim.Label;
import org.twak.mmg.prim.OBB;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;

public class FeatureFountain extends Function implements FollowOnMO {

	public MiniFacade mini;
	public Feature f;
	public Label label;

	public FeatureFountain() {
		outputType = List.class;
		color = fixed;
	}

	public FeatureFountain( Feature f, MiniFacade mini ) {
		this();
		this.f = f;
		this.mini = mini;
		this.label = new Label (f.name().toLowerCase());
	}

	@Override
	public List<Node> createSN( InputSet inputSet, MMG mmg ) {
		List<Node> out = new ArrayList<>();

		FixedLabel labelFn = new FixedLabel( label );
		Node label = new Node( labelFn );
		out.add( label );

		Node parent = new Node( this );
		out.add( parent );

		List<OBB> rects = new ArrayList<>();
		
		for ( DRectangle r : mini.getRects( f ) ) 
			rects.add( new OBB(r.x, r.y, r.width, r.height, 0) );
		
		parent.curriedArguments.add( rects );

		return out;
	}
	
	@Override
	public Object evaluate( List<Object> params, List<Object> curry, Node node ) {
		if ( node.function == this )
			return node.curriedArguments.get(0);
		else
			return null; // label
	}

	@Override
	public String getDescription() {
		return getClass().getSimpleName();
	}

	@Override
	public Function createNextMO() {
		return new LabelledListWrapper(label);
	}
	
	public static class LabelledListWrapper extends ListWrapper {

		Label l;
		
		public LabelledListWrapper( Label l ) {
			super( OBB.class );
			
			this.l = l;
			this.color = repeat;
		}
		
	    @Override
	    public List<Node> createSN( InputSet inputSet, MMG mmg ) {
	    	
            List<Node> out = new ArrayList();

            
            Object o = inputSet.findValues().get( 0 );;
            if (!(o instanceof List)) // is label...
            	return Collections.emptyList();
            
	        List list = (List)o;
	        
	        for (int i = 0; i < list.size(); i++)
	        {
	            Node n = new Node( this );
	            n.curriedArguments.add( i );
	            out.add(n);
	        }
	        
	    	for (Node n : out)
	    		l.label( n, mmg );
	    	
	    	return out;
	    }
	}

}
