package monthly.repository

import monthly.db.Database
import spock.lang.Specification

class SqliteRecurringDismissalRepositorySpec extends Specification {

    Database database
    SqliteRecurringDismissalRepository repository

    def setup() {
        database = Database.inMemory()
        database.createSchema()
        repository = new SqliteRecurringDismissalRepository(database)
    }

    def "dismiss stores a key and findAll returns it"() {
        when:
        repository.dismiss("REVOLUT|netflix|-10")

        then:
        repository.findAll() == ["REVOLUT|netflix|-10"] as Set
    }

    def "dismissing the same key twice is harmless"() {
        given:
        repository.dismiss("REVOLUT|netflix|-10")

        when:
        repository.dismiss("REVOLUT|netflix|-10")

        then:
        repository.findAll().size() == 1
    }

    def "restore removes a dismissed key"() {
        given:
        repository.dismiss("REVOLUT|netflix|-10")

        when:
        repository.restore("REVOLUT|netflix|-10")

        then:
        repository.findAll().isEmpty()
    }

    def "restoring a key that was never dismissed is harmless"() {
        when:
        repository.restore("SANTANDER|gym|-30")

        then:
        repository.findAll().isEmpty()
    }

    def "findAll is empty when nothing is dismissed"() {
        expect:
        repository.findAll().isEmpty()
    }
}