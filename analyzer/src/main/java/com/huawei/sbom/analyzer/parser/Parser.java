package com.huawei.sbom.analyzer.parser;

import org.ossreviewtoolkit.model.CuratedPackage;

import java.util.Set;

public interface Parser {
    Set<CuratedPackage> parse();
}
