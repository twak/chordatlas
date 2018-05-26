package org.twak.viewTrace.franken.style.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.twak.utils.Mathz;
import org.twak.utils.Stringz;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.AutoTextField;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.ListRightLayout;
import org.twak.utils.ui.WindowManager;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.BlockApp;
import org.twak.viewTrace.franken.BuildingApp;
import org.twak.viewTrace.franken.FacadeLabelApp;
import org.twak.viewTrace.franken.FacadeSuper;
import org.twak.viewTrace.franken.FacadeTexApp;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.PanesLabelApp;
import org.twak.viewTrace.franken.PanesTexApp;
import org.twak.viewTrace.franken.RoofApp;
import org.twak.viewTrace.franken.style.JointDistribution;
import org.twak.viewTrace.franken.style.JointDistribution.Joint;
import org.twak.viewTrace.franken.style.MultiModal;


public class JointUI extends JPanel {

	private JointDistribution jd;
	private Joint selectedJoint;
	JFrame frame;
	Runnable globalUpdate;
	
	final static List<NetSelect> nets = new ArrayList<>();
	static NetSelect DEFAULT_NET;
	
	JPanel modalPanel;
	MultiModalEditor modal;
	
	static {
		nets.add (new NetSelect(BlockApp      .class, false ) );
		nets.add (new NetSelect(BuildingApp   .class, false ) );
		nets.add (new NetSelect(FacadeLabelApp.class, true  ) );
		nets.add (new NetSelect(FacadeSuper   .class, true  ) );
		nets.add (DEFAULT_NET = new NetSelect(FacadeTexApp  .class, true  ) );
		nets.add (new NetSelect(PanesLabelApp .class, true  ) );
		nets.add (new NetSelect(PanesTexApp   .class, true  ) );
		nets.add (new NetSelect(RoofApp       .class, true  ) );
	}
	
	static class NetSelect {
		
		Class<? extends App> klass;
		boolean show;
		
		public NetSelect( Class<? extends App> k, boolean s ) {
			this.klass = k;
			this.show = s;
		}

		public App findExemplar (App root) {
			
			if (root.getClass() == klass)
				return root;
			
			return findExemplar( Collections.singletonList( root ) );
		}
		
		public App findExemplar (List<App> root) {
			
			List<App> next = new ArrayList<>();
			
			for (App a : root)
				for (App b : a.getDown().valueList())
					if (b.getClass() == klass)
						return b;
					else
						next.add(b);
			
			if (next.isEmpty())
				return null;
			
			return findExemplar( next );
		}
		
		public Component createUI(JointUI ui, ButtonGroup bg) {
			
			JPanel panel = new JPanel();
			panel.setBorder( MultiModalEditor.BORDER );
			
			JToggleButton select = new JToggleButton ( Stringz.splitCamelCase( klass.getSimpleName() ) );
			bg.add( select );
			
			select.addActionListener(  e -> ui.netSelected (this) );
			panel.setPreferredSize( new Dimension (140, 40) );
			
			panel.add (select);
			
			return panel;
		}
	}
	
	public JointUI (JointDistribution jd, Runnable globalUpdate) {
		
		this.jd = jd;
		
		if (jd.joints.isEmpty())
			addJoint();
		
		this.globalUpdate = globalUpdate;
		
		this.selectedJoint = jd.joints.get(0);
		buildUI();
	}

	private Joint addJoint() {
		Joint j = jd.rollJoint("joint", nets.stream().map( n -> n.klass ).collect(Collectors.toList()));
		
		for (NetSelect ns : nets) {
			System.out.println(">>>> " + ns.klass);
			j.appInfo.get( ns.klass ).dist = new MultiModal( NetInfo.get( ns.klass ) );
		}
		
		return j;
		
	}
	
	private void selectJoint( Joint j ) {
		if (j == selectedJoint)
			return;
		
		this.selectedJoint = j;
		buildUI();
	}

	public void netSelected( NetSelect ns ) {
		
		modalPanel.removeAll();
		
		if (modal != null)
			modal.stop();
		
		modalPanel.add (modal = new MultiModalEditor( 
				selectedJoint.appInfo.get( ns.klass ).dist, NetInfo.index.get( ns.klass ), globalUpdate ), 
				BorderLayout.CENTER );
		
		modalPanel.revalidate();
	}

	public void buildUI () {
		
		removeAll();
		
		JPanel top = new JPanel (new BorderLayout());
		top.add (jointUI(), BorderLayout.WEST);
		top.add (netsUI(), BorderLayout.CENTER);
		
		setLayout( new BorderLayout() );
		add (top, BorderLayout.NORTH);
		add (modalPanel = new JPanel(new BorderLayout()), BorderLayout.CENTER);
		
		netSelected( DEFAULT_NET );
	}
	
	
	private JPanel netsUI() {
		
		JPanel out = new JPanel( new ListRightLayout() );

		ButtonGroup netBG;
		netBG = new ButtonGroup();
		for (NetSelect c : nets) {
			if (c.show)
				out.add( c.createUI(this, netBG) );
		}
		
		return out;
	}

	public JPanel jointUI() {
		
		JPanel panel = new JPanel();
		
		panel.setLayout( new ListDownLayout() );
		
		AutoTextField atf = new AutoTextField(selectedJoint, "name", "name");
		
		panel.add( atf );
		
		JPanel newDelete = new JPanel(new FlowLayout( FlowLayout.CENTER ));
		
		JButton n = new JButton("+");
		n.addActionListener( e -> { Joint j = addJoint(); selectJoint( j ); } );
		
		JButton d = new JButton("-");
		n.addActionListener( e -> killJoint() );
		
		JButton lt = new JButton("<");
		lt.addActionListener( e -> deltaJoint(-1) );
		JButton gt = new JButton(">");
		gt.addActionListener( e -> deltaJoint(1) );
		
		AutoDoubleSlider prob = new AutoDoubleSlider( selectedJoint, "probability", "p", 0, 1 ) {
			public void updated( double p ) {
				selectedJoint.probability = p;
				jd.updateJointProb();
				globalUpdate.run();
			}
		};
		
		
		JButton save = new JButton("save...");
		save.addActionListener( e -> save(selectedJoint) );
		
		
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
		panel.add(prob);
		
		panel.add(save);
		
		panel.setPreferredSize( new Dimension (200, 60) );
		
		return panel;
	}

	private Object save( Joint selectedJoint2 ) {
		// TODO Auto-generated method stub
		return null;
	}

	private void deltaJoint( int i ) {
		
		int si= 0;
		
		
		if (selectedJoint != null)
			si = jd.joints.indexOf( selectedJoint ) ;
		
		si = ( si + i + jd.joints.size() ) % jd.joints.size();
		
		selectJoint( jd.joints.get(si) );
	}

	private void killJoint() {
		
		if ( jd.joints.size() == 1 ) {
			JOptionPane.showMessageDialog( frame, "can't delete only distribution" );
			return;
		}
		
		int si = jd.joints.indexOf( selectedJoint ) ;
		jd.joints.remove (selectedJoint);
		
		selectJoint( jd.joints.get (Mathz.clamp ( si + 1, 0, jd.joints.size()-1  )) );
	}
	
	public void openFrame() {
		
		frame = WindowManager.frame( "joint-dist editor",  this);
		
		frame.addWindowListener( new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (modal != null)
					modal.stop();
				
				globalUpdate.run();
			};
		} );
		
		frame.pack();
		frame.setVisible( true );
	}

	
	
}
