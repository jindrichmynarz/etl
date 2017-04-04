package com.linkedpipes.etl.test.dataunit;

import java.io.File;

public interface Checkable {

    boolean checkContent(File expectedContent);

}
