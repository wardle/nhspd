# Changelog

## v2.0.x (2025)

**Major architectural change**: Replace Lucene-based directory index with SQLite database.

- Rewrite using SQLite backend for better performance and reliability
- Add CLI interface with `create`, `update`, `import`, `serve`, `status` commands  
- Add column profiles (core, active, current, all) to control database size
- Distribute executable JAR via GitHub releases
- Add comprehensive test suite with property-based testing
- Update dependencies: next.jdbc, sqlite-jdbc 3.50.3.0
- Add GitHub Actions CI/CD pipeline
- Update README with new usage examples

## v1.1.x (2022-2024)

- Update quarterly NHSPD release metadata through May 2025
- Switch HTTP client from clj-http to hato for better performance
- Upgrade to Lucene 9 with breaking index format changes  
- Change license to Eclipse Public License v2.0
- Update geocoordinates dependency without Clojure >1.11 warnings
- Migrate from depstar to tools.build
- Improve type hints and clean up code

## v1.0.x (2020-2022)

- Initial release with Lucene-based directory indexing
- Postcode lookup and coordinate conversion
- Web service with REST API
- Command-line tools for index creation