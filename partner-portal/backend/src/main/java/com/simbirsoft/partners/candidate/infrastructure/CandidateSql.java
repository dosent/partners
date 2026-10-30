package com.simbirsoft.partners.candidate.infrastructure;
import com.simbirsoft.partners.shared.persistence.SqlQuery;
enum CandidateSql implements SqlQuery {
    RESERVE("creation-reserve"), FIND_CREATION("creation-find");
    private final String file;
    CandidateSql(String file) {this.file=file;}
    public String resourcePath() {return "sql/candidate/"+file+".sql";}
}
