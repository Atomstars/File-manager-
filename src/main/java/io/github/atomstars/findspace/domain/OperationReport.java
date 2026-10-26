package io.github.atomstars.findspace.domain;

import java.util.List;

public record OperationReport(int completed, List<String> problems) {
  public OperationReport {
    problems = List.copyOf(problems);
  }

  public String summary() {
    return completed + " completed · " + problems.size() + " skipped";
  }
}
