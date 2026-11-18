import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Floorcast — beacon floor tracking",
  description:
    "Receive beacon locations from Beacon Simulator and plot live floor tracking with movement paths.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
