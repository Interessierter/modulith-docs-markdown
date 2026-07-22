package li.selman.modulithdocsmarkdown.sample.order;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record OrderId(String value) {}
