package org.twak.tweed;

import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;

import org.twak.tweed.gen.Gen;

public class TweedSave {

	public List<Gen> gens = new ArrayList();
	
	public TweedSave(List<Gen> genList) {
		gens = new ArrayList(genList);
	}

}
