package monthly.repository

import monthly.db.Database
import spock.lang.Specification

class SqliteTransferRepositorySpec extends Specification {

    Database database
    SqliteTransferRepository repository

    def setup() {
        database = Database.inMemory()
        database.createSchema()
        repository = new SqliteTransferRepository(database)
    }

    def "mark then findAll returns the fingerprint"() {
        when: repository.mark("fp1")
        then: repository.findAll() == ["fp1"] as Set
    }

    def "mark is idempotent: marking twice keeps one entry"() {
        given: repository.mark("fp1")
        when:  repository.mark("fp1")
        then:  repository.findAll() == ["fp1"] as Set
    }

    def "unmark removes the flag"() {
        given: repository.mark("fp1")
        when:  repository.unmark("fp1")
        then:  repository.findAll().isEmpty()
    }
}