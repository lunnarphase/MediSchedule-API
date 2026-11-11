import { createRoot } from "react-dom/client";
import "./styles.css";

function App() {
  return (
    <main className="app">
      <h1>MediSchedule</h1>
      <p>Nowy panel dostępny wkrótce</p>
    </main>
  );
}

createRoot(document.getElementById("root")).render(<App />);
