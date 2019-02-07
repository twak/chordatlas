package org.twak.mmg.functions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.twak.mmg.Command;
import org.twak.mmg.InputSet;
import org.twak.mmg.MMG;
import org.twak.mmg.MOgram;
import org.twak.mmg.Node;

public class LabellingListWrapper extends ListWrapper {

	Node l; // fixme, should take label from walk

	public LabellingListWrapper( Node nodeLabel ) {
		super();

		this.l = nodeLabel;
		this.color = repeat;
	}

	@Override
	public List<Node> createSN( InputSet inputSet, List<Object> vals, MMG mmg, Command mo, MOgram mogram ) {

		List<Node> out = new ArrayList();

		Object o = inputSet.findValues().get( 0 );

		List list = (List) o;

		for ( int i = 0; i < list.size(); i++ ) {
			Node n = new Node( this );
			n.curriedArguments.add( i );
			out.add( n );
		}

		AddLabel.label( Collections.singleton( l ), inputSet.stream() );

		return out;
	}
}