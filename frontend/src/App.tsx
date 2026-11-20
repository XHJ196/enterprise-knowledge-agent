
import './App.css'

function App() {
  return (
    <div className="app">
      <header className="header">
        <h1>Enterprise Knowledge Agent</h1>
        <p>Enterprise AI Knowledge Platform</p>
      </header>

      <main className="main">
        <section className="welcome-card">
          <h2>Welcome to Knowledge Platform</h2>
          <p>
            Your intelligent enterprise knowledge assistant.
          </p>
          <button>Get Started</button>
        </section>

        <section className="service-card">
          <h3>System Services</h3>
          <p>Spring Boot: Port 8080</p>
          <p>FastAPI: Port 8000</p>
          <p>Frontend: Port 5173</p>
        </section>
      </main>
    </div>
  )
}

export default App
