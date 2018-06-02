package org.twak.tweed.gen.skel;

import org.twak.camp.Tag;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;

public class SETag extends Tag {

	public SuperEdge se;
	public SuperFace sf;
	
	public SETag( SuperEdge se, SuperFace sf ) {
		this.se = se;
		this.sf = sf;
	}

}
