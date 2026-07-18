package monthly.repository

import monthly.db.Database
import spock.lang.Specification

class SqliteRecurringNameRepositorySpec extends Specification {

    Database database
    SqliteRecurringNameRepository repository

    def setup() {
        database = Database.inMemory()
        database.createSchema()
        repository = new SqliteRecurringNameRepository(database)
    }

    def "set stores a name and findAll returns it by key"() {
        when:
        repository.set("REVOLUT|netflix|-10", "Netflix")

        then:
        repository.findAll() == ["REVOLUT|netflix|-10": "Netflix"]
    }

    def "set overwrites the name for the same key"() {
        given:
        repository.set("REVOLUT|netflix|-10", "Netflix")

        when:
        repository.set("REVOLUT|netflix|-10", "Netflix Premium")

        then:
        repository.findAll()["REVOLUT|netflix|-10"] == "Netflix Premium"
    }

    def "clear removes a stored name"() {
        given:
        repository.set("REVOLUT|netflix|-10", "Netflix")

        when:
        repository.clear("REVOLUT|netflix|-10")

        then:
        repository.findAll().isEmpty()
    }

    def "findAll is empty when nothing is stored"() {
        expect:
        repository.findAll().isEmpty()
    }
}