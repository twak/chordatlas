package org.twak.viewTrace.franken.style.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.twak.utils.Mathz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.AutoTextField;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.style.JointDistribution;
import org.twak.viewTrace.franken.style.JointDistribution.Joint;

public class JointUI extends JPanel {

	private JointDistribution jd;
	private Joint selectedJoint;
	
	public JointUI (JointDistribution jd) {
		this.jd = jd;
		
		updateUI();
	}
	
	public void updateUI () {
		removeAll();
		
		setLayout( new BorderLayout() );;
		
		add (jointUI(), BorderLayout.WEST);
		
		add (classUI(), BorderLayout.CENTER);
		
	}
	
	private JPanel classUI() {
		
		JPanel out = new JPanel();

		classUI (jd.root, out);
		
		return out;
	}

	private void classUI( App root, JPanel out ) {
		
//		MultiMap<String, App> downs = root.getDown();
//		for (String s : downs.keySet()) {
//			App a = downs.get( s ).get( 0 );
//			
//			classUI ()
//			
//		}
		
		
	}

	public JPanel jointUI() {
		
		JPanel panel = new JPanel();
		
		panel.setLayout( new ListDownLayout() );
		
		AutoTextField atf = new AutoTextField(selectedJoint == null ? new Joint() : selectedJoint, "name", "name");
		
		panel.add( atf );
		
		JPanel newDelete = new JPanel(new FlowLayout( FlowLayout.CENTER ));
		
		JButton n = new JButton("+");
		n.addActionListener( e -> newJoint() );
		
		JButton d = new JButton("-");
		n.addActionListener( e -> killJoint() );
		
		JButton lt = new JButton("<");
		lt.addActionListener( e -> deltaJoint(-1) );
		JButton gt = new JButton(">");
		gt.addActionListener( e -> deltaJoint(1) );
		
		
		newDelete.add(n);
		
		newDelete.add(d);
		newDelete.add(lt);
		newDelete.add(gt);

		if (selectedJoint == null) {
			d.setEnabled( false );
			lt.setEnabled( false );
			gt.setEnabled( false );
			atf.text.setEditable( false );
		}
		
		panel.add(newDelete);
		
		return panel;
	}

	private void deltaJoint( int i ) {
		
		int si= 0;
		
		
		if (selectedJoint != null)
			si = jd.joints.indexOf( selectedJoint ) ;
		
		si = ( si + i + jd.joints.size() ) % jd.joints.size();
		
		if (jd.joints.isEmpty())
			selectedJoint = null;
		else
			selectedJoint = jd.joints.get(si);
		
		updateUI();
	}

	private void killJoint() {
		
		int si = jd.joints.indexOf( selectedJoint ) ;
		jd.joints.remove (selectedJoint);
		
		try {
			selectedJoint = jd.joints.get (Mathz.clamp ( si + 1, 0, jd.joints.size()-1  ));
		}
		catch ( ArrayIndexOutOfBoundsException e ) {
			selectedJoint = null;
		}
		
		updateUI();
	}
	
	private void newJoint() {
		selectedJoint =  new Joint();
		jd.joints.add(selectedJoint);
		jd.updateJointProb();
		updateUI();
	}
	
	
}
