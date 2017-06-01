package org.twak.tweed;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.tweed.gen.Gen;
import org.twak.utils.ListRightLayout;
import org.twak.utils.WeakListener;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.SimplePopup2;

public class GenListItem extends JPanel {
	
	JCheckBox visible;
	JPanel color;
	Gen gen;
	
	Changed selectionListener;
	
	public GenListItem ( Gen gen, WeakListener selectedGenListener, TweedFrame tweed, ActionListener onClick ) {
		
		this.gen = gen;
		
		selectedGenListener.add(selectionListener = new Changed() {
			@Override
			public void changed() {
				GenListItem.this.setBackground( tweed.selectedGen == gen ? Color.lightGray : Color.white);
			}
		});
		
		setBorder( new EmptyBorder(3, 3, 3, 3) );
		
		setLayout( new ListRightLayout() );

		color = new JPanel();
		visible = new JCheckBox();
		
		int d = visible.getPreferredSize().height;
		color.setPreferredSize( new Dimension ( d,d ) ) ;
		
		add(color);
		color.setBackground(gen.color);
		
		color.addMouseListener(new MouseAdapter() {
			
			public void mousePressed(MouseEvent e) {
				
				new ColourPicker(gen.color) {
					
					@Override
					public void picked(Color c) {
						gen.setColor ( c );
						color.setBackground(gen.color);
					}
				};
			}
		});

		add(visible);
		visible.setSelected(gen.visible);
		visible.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent arg0) {
				gen.setVisible( visible.isSelected() );
			}
		});
		
		JLabel name = new JLabel(gen.name);
		add(name);
		
		
		MouseListener ml = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (e.getButton() == 3)
					showMenu(e);
				else
					onClick.actionPerformed(null);
			}

			private void showMenu( MouseEvent e ) {
				SimplePopup2 pop = new SimplePopup2( e );
				pop.add( "delete", new Runnable() {

					@Override
					public void run() {
						tweed.removeGen( gen );
					}
				});
				
				pop.add( "delete w/below", new Runnable() {
					
					@Override
					public void run() {
						tweed.removeBelowGen( gen );
					}
				});
				pop.show();
			}
		};
		
		name.addMouseListener(ml);
		
		addMouseListener(ml);
	}
	
}
