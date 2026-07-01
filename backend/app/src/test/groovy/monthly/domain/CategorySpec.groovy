package monthly.domain

import spock.lang.Specification

class CategorySpec extends Specification {

    def "INCOME is not user-assignable"() {
        expect: !Category.INCOME.isAssignable()
    }

    def "every non-INCOME category is assignable"() {
        expect: Category.values().findAll { it != Category.INCOME }.every { it.isAssignable() }
    }
}