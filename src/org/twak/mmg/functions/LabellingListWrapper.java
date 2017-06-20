package org.twak.mmg.functions;

import java.util.ArrayList;
import java.util.List;

import org.twak.mmg.InputSet;
import org.twak.mmg.MMG;
import org.twak.mmg.Node;
import org.twak.mmg.prim.Label;
import org.twak.mmg.prim.OBB;

public class LabellingListWrapper extends ListWrapper {

	Label l;
	
	public LabellingListWrapper( Label l ) {
		super( OBB.class );
		
		this.l = l;
		this.color = repeat;
	}
	
    @Override
    public List<Node> createSN( InputSet inputSet, MMG mmg ) {
    	
        List<Node> out = new ArrayList();
        
        Object o = inputSet.findValues().get( 0 );;
        
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