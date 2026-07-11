package monthly.repository

import monthly.db.Database
import monthly.domain.Category
import spock.lang.Specification

class SqliteBudgetRepositorySpec extends Specification {

    Database database
    SqliteBudgetRepository repository

    def setup() {
        database = Database.inMemory()
        database.createSchema()
        repository = new SqliteBudgetRepository(database)
    }

    def "set then findAll returns the budget"() {
        when: repository.set(Category.GROCERIES, new BigDecimal("400.00"))
        then: repository.findAll() == [(Category.GROCERIES): new BigDecimal("400.00")]
    }

    def "set is an upsert: second call replaces the limit"() {
        given: repository.set(Category.GROCERIES, new BigDecimal("400.00"))
        when:  repository.set(Category.GROCERIES, new BigDecimal("350.00"))
        then:  repository.findAll() == [(Category.GROCERIES): new BigDecimal("350.00")]
    }

    def "clear removes the budget"() {
        given: repository.set(Category.GROCERIES, new BigDecimal("400.00"))
        when:  repository.clear(Category.GROCERIES)
        then:  repository.findAll().isEmpty()
    }
}