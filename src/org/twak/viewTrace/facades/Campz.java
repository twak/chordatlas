package org.twak.viewTrace.facades;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.twak.straightskeleton.Output;
import org.twak.straightskeleton.Output.Face;
import org.twak.utils.MultiMap;

public class Campz {

		public static List<List<Face>> findChains (Output output) {
			
			Set<Face> remaining = new LinkedHashSet<>(output.faces.values());

			MultiMap<Face, Face> parent2children = new MultiMap<>();
			
			while (!remaining.isEmpty()) {
				
				Set<Face> above = new LinkedHashSet<>();
				
				Face f = remaining.iterator().next();
				
				do {
					remaining.remove( f );
					above.add( f );
					
					if ( f.parent != null )
						f = f.parent;
					
				} while ( f.parent != null );
				
				above.add(f);

				parent2children.putAll( f, above, true );
			}
			
			return parent2children.keySet().stream().map ( f -> parent2children.get(f) ).collect( Collectors.toList() );
		}
		
		
}
