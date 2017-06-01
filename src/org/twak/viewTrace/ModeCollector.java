package org.twak.viewTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class ModeCollector implements Collector<Double, List<Double>, Double> {

	double frac;
	
	public ModeCollector() {
		this (0.5);
		
	}
	public ModeCollector(double frac) {
		this.frac = frac;
	}
	
	@Override
	public BiConsumer<List<Double>, Double> accumulator() {
		return (list, v) -> list.add(v);
	}

	@Override
	public Set<java.util.stream.Collector.Characteristics> characteristics() {
		return EnumSet.of(Characteristics.UNORDERED);
	}

	@Override
	public BinaryOperator<List<Double>> combiner() {
		
		return new BinaryOperator<List<Double>>() {

			@Override
			public List<Double> apply( List<Double> t, List<Double> u ) {
				t.addAll(u);
				return t;
			}
		};
	}

	@Override
	public Function<List<Double>, Double> finisher() {
		return (x) -> {
			if ( x.isEmpty() )
				return Double.NaN;
			
			Collections.sort (x);
			
			return x.get( (int) ( x.size() * frac) );
		};
	}

	@Override
	public Supplier<List<Double>> supplier() {
		return () -> new ArrayList<>();
	}



}
