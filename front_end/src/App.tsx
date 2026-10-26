import { BrowserRouter } from "react-router-dom";
import AppRoutes from "./routes/AppRoutes";
import ScrollToTop from "./components/common/ScrollToTop/ScrollToTop";
import { AuthProvider } from "./context/AuthContext";

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ScrollToTop />
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;