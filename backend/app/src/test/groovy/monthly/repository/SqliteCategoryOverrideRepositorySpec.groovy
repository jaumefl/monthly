package monthly.repository

import monthly.db.Database
import monthly.domain.Category
import spock.lang.Specification

class SqliteCategoryOverrideRepositorySpec extends Specification {

    Database database
    SqliteCategoryOverrideRepository repository

    def setup() {
        database = Database.inMemory()
        database.createSchema()
        repository = new SqliteCategoryOverrideRepository(database)
    }

    def "set then findAll returns the override"() {
        when: repository.set("fp1", Category.GROCERIES)
        then: repository.findAll() == ["fp1": Category.GROCERIES]
    }

    def "set is an upsert: second call replaces the category"() {
        given: repository.set("fp1", Category.GROCERIES)
        when:  repository.set("fp1", Category.EATING_OUT)
        then:  repository.findAll() == ["fp1": Category.EATING_OUT]
    }

    def "clear removes the override"() {
        given: repository.set("fp1", Category.GROCERIES)
        when:  repository.clear("fp1")
        then:  repository.findAll().isEmpty()
    }
}